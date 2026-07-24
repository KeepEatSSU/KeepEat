package com.keepeat.backend.domain.admin;

import com.keepeat.backend.domain.admin.dto.AdminLoginForm;
import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.user.dto.LoginRequest;
import com.keepeat.backend.domain.user.dto.TokenResponse;
import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.service.AppUserService;
import com.keepeat.backend.domain.user.service.EmailNormalizer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import com.keepeat.backend.domain.security.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String ADMIN_TOKEN_COOKIE = "ADMIN_TOKEN";
    private static final int ACCESS_TOKEN_MAX_AGE_SECONDS = 3600;

    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;
    private final RequestRateLimiter rateLimiter;

    @Value("${app.security.admin-cookie-secure:false}")
    private boolean adminCookieSecure;

    @GetMapping("/login")
    public String loginForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AdminLoginForm());
        }
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("form") AdminLoginForm form,
                        BindingResult bindingResult,
                        HttpServletResponse response,
                        HttpServletRequest request,
                        Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/login";
        }

        try {
            rateLimiter.check(request, "admin-login", EmailNormalizer.normalize(form.getEmail()), 5, Duration.ofMinutes(15));
            AppUser user = appUserRepository.findByEmailIgnoreCase(EmailNormalizer.normalize(form.getEmail()))
                    .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));
            if (user.getRole() != Role.ROLE_ADMIN) {
                throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
            }

            TokenResponse tokens = appUserService.login(new LoginRequest(form.getEmail(), form.getPassword()));

            response.addCookie(buildAdminCookie(tokens.getAccessToken(), ACCESS_TOKEN_MAX_AGE_SECONDS));
            return "redirect:/admin/ingredients";
        } catch (KeepEatException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/login";
        }
    }

    @PostMapping("/logout")
    public String logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            String sessionId = authentication.getDetails() instanceof String value ? value : null;
            appUserService.logout(userId, sessionId, null);
        }
        response.addCookie(buildAdminCookie("", 0));
        return "redirect:/admin/login";
    }

    private Cookie buildAdminCookie(String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(ADMIN_TOKEN_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(adminCookieSecure);
        cookie.setPath("/admin");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
