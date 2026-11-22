import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expo Push API mock (#52). 의존성 없는 단일 파일이며 `java ExpoMock.java`로 실행한다.
 *
 * 엔드포인트
 *   POST /--/api/v2/push/send          ticket 발급 (단건 객체 또는 배열)
 *   POST /--/api/v2/push/getReceipts   receipt 조회
 *   GET  /__perf/config                현재 장애 설정
 *   POST /__perf/config?key=value&...  장애 설정 변경 (지정한 키만 바뀐다)
 *   POST /__perf/reset?runId=xxx       설정·receipt 초기화, 기록 파일을 /runs/{runId}/mock.jsonl로 바꾼다
 *
 * 기록: 요청·결과를 한 줄에 하나씩 JSON으로 남긴다. 집계 스크립트가 시드·DB와 대조한다.
 *
 * 자체 검사: `java ExpoMock.java --selfcheck` (지연 보간 분위수 확인)
 */
public class ExpoMock {

    // ---------- 지연 분포: 실측 분위수 사이 선형 보간 (E4) ----------
    // Knock 실측 p50 72 / p90 100 / p95 138 / p99 575ms.
    // p0 40ms와 상한 1,759ms(하루 최고 p99)는 실측이 없어 정한 가정값이다.
    static final double[] Q = {0.0, 0.50, 0.90, 0.95, 0.99, 1.0};

    static double sampleLatencyMs(Config c, double u) {
        double[] v = {40, 72, 100, 138, c.p99Ms, Math.max(1759, c.p99Ms)};
        for (int i = 1; i < Q.length; i++) {
            if (u <= Q[i]) {
                double t = (u - Q[i - 1]) / (Q[i] - Q[i - 1]);
                return v[i - 1] + t * (v[i] - v[i - 1]);
            }
        }
        return v[v.length - 1];
    }

    // ---------- 장애 설정 ----------
    static final class Config {
        double p99Ms = 575;               // ST3: 575 / 1759 / 5000
        double rate5xx = 0;               // F2: 500 응답
        double rateTimeout = 0;           // F2: 응답 없이 timeoutMs 대기 후 끊음 (접수 안 됨)
        double rateTimeoutAccepted = 0;   // F2: 접수(ticket 발급)는 하고 응답만 timeoutMs 늦음 → 앱 재시도 시 중복
        double rateDrop = 0;              // 응답 없이 연결 닫기
        long timeoutMs = 6000;            // 앱 응답 타임아웃(5초)보다 길게
        long force429UntilMs = 0;         // F4: 이 시각까지 모든 send에 429
        int rateLimitPerSec = 600;        // Expo 프로젝트당 초당 한도
        int maxMessagesPerRequest = 100;
        double receiptMissingRate = 0;    // F5: receipt가 끝까지 안 나옴
        long receiptDelayMs = 1000;       // F3·F5: ticket 발급 후 receipt가 조회 가능해지는 시간

        void apply(Map<String, String> q) {
            q.forEach((k, v) -> {
                switch (k) {
                    case "p99Ms" -> p99Ms = Double.parseDouble(v);
                    case "rate5xx" -> rate5xx = Double.parseDouble(v);
                    case "rateTimeout" -> rateTimeout = Double.parseDouble(v);
                    case "rateTimeoutAccepted" -> rateTimeoutAccepted = Double.parseDouble(v);
                    case "rateDrop" -> rateDrop = Double.parseDouble(v);
                    case "timeoutMs" -> timeoutMs = Long.parseLong(v);
                    case "force429Seconds" -> force429UntilMs = System.currentTimeMillis() + Long.parseLong(v) * 1000;
                    case "rateLimitPerSec" -> rateLimitPerSec = Integer.parseInt(v);
                    case "receiptMissingRate" -> receiptMissingRate = Double.parseDouble(v);
                    case "receiptDelayMs" -> receiptDelayMs = Long.parseLong(v);
                    default -> throw new IllegalArgumentException("unknown config key: " + k);
                }
            });
        }

        String toJson() {
            return "{\"p99Ms\":" + p99Ms + ",\"rate5xx\":" + rate5xx + ",\"rateTimeout\":" + rateTimeout
                    + ",\"rateTimeoutAccepted\":" + rateTimeoutAccepted + ",\"rateDrop\":" + rateDrop
                    + ",\"timeoutMs\":" + timeoutMs + ",\"force429UntilMs\":" + force429UntilMs
                    + ",\"rateLimitPerSec\":" + rateLimitPerSec + ",\"maxMessagesPerRequest\":" + maxMessagesPerRequest
                    + ",\"receiptMissingRate\":" + receiptMissingRate + ",\"receiptDelayMs\":" + receiptDelayMs + "}";
        }
    }

    record Receipt(long availableAtMs) {
    }

    static volatile Config config = new Config();
    static final Map<String, Receipt> receipts = new ConcurrentHashMap<>();

    // 초당 한도: 현재 초와 그 초의 요청 수
    static long windowSec = 0;
    static int windowCount = 0;

    static synchronized boolean overRateLimit(long nowMs, int limit) {
        long sec = nowMs / 1000;
        if (sec != windowSec) {
            windowSec = sec;
            windowCount = 0;
        }
        return ++windowCount > limit;
    }

    // ---------- 기록 ----------
    static BufferedWriter log;

    static synchronized void record(String json) {
        try {
            log.write(json);
            log.newLine();
            log.flush(); // 앱 강제 종료 시나리오에서도 mock 기록은 남아야 한다
        } catch (IOException e) {
            System.err.println("log write failed: " + e);
        }
    }

    static synchronized void openLog(String runId) throws IOException {
        if (log != null) {
            log.close();
        }
        Path dir = Path.of(System.getenv().getOrDefault("RUNS_DIR", "/runs"), runId);
        Files.createDirectories(dir);
        log = Files.newBufferedWriter(dir.resolve("mock.jsonl"), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    // ---------- 요청 파싱 ----------
    // JSON 라이브러리 없이 정규식으로 뽑는다. 앱이 보내는 형식(to, data.notificationId)이 고정이라 충분하다.
    // 배열 요청이면 to와 notificationId를 등장 순서로 짝짓는다.
    static final Pattern TO = Pattern.compile("\"to\"\\s*:\\s*\"([^\"]*)\"");
    static final Pattern NID = Pattern.compile("\"notificationId\"\\s*:\\s*(\\d+)");
    static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    static List<String> all(Pattern p, String s) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    static String esc(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---------- send ----------
    static void send(HttpExchange ex) throws IOException {
        long now = System.currentTimeMillis();
        Config c = config;
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean isArray = body.stripLeading().startsWith("[");
        List<String> tokens = all(TO, body);
        List<String> nids = all(NID, body);
        String firstToken = tokens.isEmpty() ? null : tokens.get(0);
        String firstNid = nids.isEmpty() ? null : nids.get(0);
        String base = "{\"ts\":" + now + ",\"ev\":\"send\",\"token\":" + esc(firstToken) + ",\"nid\":" + firstNid
                + ",\"count\":" + tokens.size();

        if (now < c.force429UntilMs || overRateLimit(now, c.rateLimitPerSec)) {
            record(base + ",\"outcome\":\"429\"}");
            respond(ex, 429, "{\"errors\":[{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"rate limit exceeded\"}]}");
            return;
        }
        if (tokens.size() > c.maxMessagesPerRequest) {
            record(base + ",\"outcome\":\"too_many\"}");
            respond(ex, 400, "{\"errors\":[{\"code\":\"PUSH_TOO_MANY_NOTIFICATIONS\",\"message\":\"max 100\"}]}");
            return;
        }

        double u = ThreadLocalRandom.current().nextDouble();
        if ((u -= c.rate5xx) < 0) {
            sleep((long) sampleLatencyMs(c, ThreadLocalRandom.current().nextDouble()));
            record(base + ",\"outcome\":\"5xx\"}");
            respond(ex, 500, "{\"errors\":[{\"code\":\"INTERNAL_SERVER_ERROR\"}]}");
            return;
        }
        if ((u -= c.rateTimeout) < 0) {
            record(base + ",\"outcome\":\"timeout\"}");
            sleep(c.timeoutMs);
            ex.close();
            return;
        }
        if ((u -= c.rateDrop) < 0) {
            record(base + ",\"outcome\":\"drop\"}");
            ex.close(); // 응답 헤더 없이 닫는다 → 클라이언트는 EOF
            return;
        }
        boolean acceptedButLate = (u -= c.rateTimeoutAccepted) < 0;

        long delay = acceptedButLate ? c.timeoutMs : (long) sampleLatencyMs(c, ThreadLocalRandom.current().nextDouble());
        List<String> tickets = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String nid = i < nids.size() ? nids.get(i) : null;
            String line = "{\"ts\":" + now + ",\"ev\":\"ticket\",\"token\":" + esc(token) + ",\"nid\":" + nid
                    + ",\"delayMs\":" + delay;
            // F7: 시드가 만료 토큰 이름에 "expired"를 넣는다
            if (token.contains("expired")) {
                record(line + ",\"outcome\":\"DeviceNotRegistered\"}");
                tickets.add("{\"status\":\"error\",\"message\":\"not registered\",\"details\":{\"error\":\"DeviceNotRegistered\"}}");
                continue;
            }
            String id = UUID.randomUUID().toString();
            boolean missing = ThreadLocalRandom.current().nextDouble() < c.receiptMissingRate;
            if (!missing) {
                receipts.put(id, new Receipt(now + c.receiptDelayMs));
            }
            record(line + ",\"outcome\":\"" + (acceptedButLate ? "ok_late" : "ok") + "\",\"ticket\":\"" + id
                    + "\",\"receipt\":\"" + (missing ? "never" : "planned") + "\"}");
            tickets.add("{\"status\":\"ok\",\"id\":\"" + id + "\"}");
        }
        sleep(delay);
        String data = isArray ? "[" + String.join(",", tickets) + "]" : (tickets.isEmpty() ? "{}" : tickets.get(0));
        respond(ex, 200, "{\"data\":" + data + "}");
    }

    // ---------- getReceipts ----------
    static void getReceipts(HttpExchange ex) throws IOException {
        long now = System.currentTimeMillis();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int idsAt = body.indexOf("\"ids\"");
        List<String> ids = idsAt < 0 ? List.of() : all(QUOTED, body.substring(body.indexOf('[', idsAt)));
        Map<String, String> found = new LinkedHashMap<>();
        int pending = 0;
        int unknown = 0;
        for (String id : ids) {
            Receipt r = receipts.get(id);
            if (r == null) {
                unknown++; // 누락 대상이거나 발급한 적 없는 id
            } else if (r.availableAtMs() <= now) {
                found.put(id, "{\"status\":\"ok\"}");
            } else {
                pending++;
            }
        }
        record("{\"ts\":" + now + ",\"ev\":\"receipts\",\"asked\":" + ids.size() + ",\"ok\":" + found.size()
                + ",\"pending\":" + pending + ",\"unknown\":" + unknown + ",\"okIds\":["
                + String.join(",", found.keySet().stream().map(ExpoMock::esc).toList()) + "]}");
        StringBuilder sb = new StringBuilder("{\"data\":{");
        found.forEach((id, v) -> sb.append(sb.length() > 9 ? "," : "").append(esc(id)).append(':').append(v));
        respond(ex, 200, sb.append("}}").toString());
    }

    // ---------- 관리 ----------
    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String kv : raw.split("&")) {
                int i = kv.indexOf('=');
                if (i > 0) {
                    q.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8),
                            URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return q;
    }

    static void configEndpoint(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            Config next = new Config();
            Config cur = config;
            copy(cur, next); // 지정하지 않은 키는 현재 값을 유지한다
            try {
                next.apply(query(ex));
            } catch (IllegalArgumentException e) {
                respond(ex, 400, "{\"error\":" + esc(e.getMessage()) + "}");
                return;
            }
            config = next;
            record("{\"ts\":" + System.currentTimeMillis() + ",\"ev\":\"config\",\"config\":" + next.toJson() + "}");
        }
        respond(ex, 200, config.toJson());
    }

    static void copy(Config a, Config b) {
        b.p99Ms = a.p99Ms;
        b.rate5xx = a.rate5xx;
        b.rateTimeout = a.rateTimeout;
        b.rateTimeoutAccepted = a.rateTimeoutAccepted;
        b.rateDrop = a.rateDrop;
        b.timeoutMs = a.timeoutMs;
        b.force429UntilMs = a.force429UntilMs;
        b.rateLimitPerSec = a.rateLimitPerSec;
        b.receiptMissingRate = a.receiptMissingRate;
        b.receiptDelayMs = a.receiptDelayMs;
    }

    static void reset(HttpExchange ex) throws IOException {
        String runId = query(ex).getOrDefault("runId", "default");
        if (!runId.matches("[A-Za-z0-9._-]+")) {
            respond(ex, 400, "{\"error\":\"invalid runId\"}");
            return;
        }
        config = new Config();
        receipts.clear();
        openLog(runId);
        record("{\"ts\":" + System.currentTimeMillis() + ",\"ev\":\"reset\",\"runId\":" + esc(runId) + "}");
        respond(ex, 200, "{\"runId\":" + esc(runId) + "}");
    }

    // ---------- 공통 ----------
    static void respond(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    interface Handler {
        void handle(HttpExchange ex) throws IOException;
    }

    static void route(HttpServer server, String path, Handler h) {
        server.createContext(path, ex -> {
            try {
                h.handle(ex);
            } catch (Exception e) {
                System.err.println(path + " failed: " + e);
                ex.close();
            }
        });
    }

    // 보간은 결정적이므로 고정된 분위수 입력으로 검사한다(무작위 표본은 p99 위쪽의 가파른 구간 때문에 흔들린다)
    static void selfcheck() {
        Config c = new Config();
        double[][] expect = {{0.0, 40}, {0.25, 56}, {0.50, 72}, {0.90, 100}, {0.95, 138}, {0.97, 356.5}, {0.99, 575}, {1.0, 1759}};
        for (double[] e : expect) {
            double got = sampleLatencyMs(c, e[0]);
            if (Math.abs(got - e[1]) > 1e-6) {
                throw new AssertionError("u=" + e[0] + " expected " + e[1] + " got " + got);
            }
        }
        c.p99Ms = 5000; // ST3: p99를 올리면 상한도 함께 올라간다
        if (sampleLatencyMs(c, 0.99) != 5000 || sampleLatencyMs(c, 1.0) != 5000) {
            throw new AssertionError("p99 override");
        }
        System.out.println("selfcheck ok");
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("--selfcheck")) {
            selfcheck();
            return;
        }
        openLog("default");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 1024);
        // 지연·타임아웃 동안 스레드를 붙잡으므로 요청마다 가상 스레드를 쓴다
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        route(server, "/--/api/v2/push/send", ExpoMock::send);
        route(server, "/--/api/v2/push/getReceipts", ExpoMock::getReceipts);
        route(server, "/__perf/config", ExpoMock::configEndpoint);
        route(server, "/__perf/reset", ExpoMock::reset);
        server.start();
        System.out.println("ExpoMock listening on " + port);
    }
}
