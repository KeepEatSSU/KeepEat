import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL =
    __ENV.BASE_URL || 'http://host.docker.internal:8080';

export const options = {
    vus: 1,
    duration: '10s',
};

export default function () {
    const response = http.get(`${BASE_URL}/actuator/health/liveness`, {
        tags: {
            name: 'health',
        },
    });

    check(response, {
        'HTTP 상태 코드가 200이다': (res) => res.status === 200,

        '애플리케이션 상태가 UP이다': (res) => {
            try {
                return res.json().status === 'UP';
            } catch {
                return false;
            }
        },
    });

    sleep(1);
}