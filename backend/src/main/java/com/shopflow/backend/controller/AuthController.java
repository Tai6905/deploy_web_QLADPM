package com.shopflow.backend.controller;

import com.shopflow.backend.entity.User;
import com.shopflow.backend.service.AuthService;
import com.shopflow.backend.service.EmailAlreadyRegisteredException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.registerUser(request.getEmail(), request.getPassword(), request.getDisplayName());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
        } catch (EmailAlreadyRegisteredException | DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AuthError("Email is already registered", 409));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new AuthError(exception.getMessage(), 400));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            User user = authService.authenticateUser(request.getEmail(), request.getPassword());
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
            httpRequest.getSession(true);
            sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
            HttpSession authenticatedSession = httpRequest.getSession(true);
            authenticatedSession.setAttribute("userId", user.getId());
            authenticatedSession.setAttribute("userRole", user.getRole());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);
            return ResponseEntity.ok(toResponse(user));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(new AuthError("Invalid email or password", 401));
        }
    }

    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request, CsrfToken csrfToken) {
        csrfToken.getToken();
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Number userId)) {
            return new SessionResponse(false, null, csrfToken.getToken());
        }
        return authService.findUserById(userId.longValue())
                .filter(User::isActive)
                .map(user -> new SessionResponse(true, toResponse(user), csrfToken.getToken()))
                .orElseGet(() -> {
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                    return new SessionResponse(false, null, csrfToken.getToken());
                });
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        csrfTokenRepository.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    private AuthUserResponse toResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }
}

record AuthUserResponse(Long userId, String username, String displayName, String role) {}

record SessionResponse(boolean authenticated, AuthUserResponse user, String csrfToken) {}

record AuthError(String message, int status) {}

@Data
class RegisterRequest {
    @NotBlank @Email @Size(max = 255)
    private String email;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(max = 255)
    private String displayName;
}

@Data
class LoginRequest {
    @NotBlank @Email @Size(max = 255)
    private String email;
    @NotBlank @Size(max = 72)
    private String password;
}
