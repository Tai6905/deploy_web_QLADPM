package com.shopflow.backend.controller;

import com.shopflow.backend.service.AccountService;
import com.shopflow.backend.service.AccountService.AccountProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accounts;
    private final CookieCsrfTokenRepository csrf;
    public AccountController(AccountService accounts, CookieCsrfTokenRepository csrf) { this.accounts = accounts; this.csrf = csrf; }
    @GetMapping public AccountProfile profile(Authentication authentication) { return accounts.profile(authentication); }
    @PutMapping public AccountProfile update(Authentication authentication, @Valid @RequestBody UpdateProfile request) {
        return accounts.updateProfile(authentication, request.displayName(), request.phone());
    }
    @PutMapping("/password") public ResponseEntity<Void> password(Authentication authentication,
            @Valid @RequestBody ChangePassword request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        accounts.changePassword(authentication, request.currentPassword(), request.newPassword());
        var session = httpRequest.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        csrf.saveToken(null, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
    public record UpdateProfile(@NotBlank @Size(max = 255) String displayName, @Size(max = 20) String phone) {}
    public record ChangePassword(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
