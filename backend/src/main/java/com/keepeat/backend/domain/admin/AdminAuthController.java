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

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String ADMIN_TOKEN_COOKIE = "ADMIN_TOKEN";
    private static final int ACCESS_TOKEN_MAX_AGE_SECONDS = 3600;

    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;

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
                        Model model) {
        if (bindingResult.hasErrors()) {
            return "admin/login";
        }

        try {
            TokenResponse tokens = appUserService.login(new LoginRequest(form.getEmail(), form.getPassword()));

            AppUser user = appUserRepository.findByEmail(form.getEmail())
                    .orElseThrow(() -> new KeepEatException(ErrorCode.USER_NOT_FOUND));
            if (user.getRole() != Role.ROLE_ADMIN) {
                throw new KeepEatException(ErrorCode.ADMIN_ACCESS_DENIED);
            }

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
    public String logout(HttpServletResponse response) {
        response.addCookie(buildAdminCookie("", 0));
        return "redirect:/admin/login";
    }

    private Cookie buildAdminCookie(String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(ADMIN_TOKEN_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 로컬 개발용. 운영 환경에선 true 필요
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
}
