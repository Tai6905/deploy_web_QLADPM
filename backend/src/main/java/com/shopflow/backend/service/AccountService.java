package com.shopflow.backend.service;

import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final Set<String> EMPLOYEE_ROLES = Set.of("SALES_STAFF", "WAREHOUSE");
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final SessionRegistry sessions;

    public AccountService(UserRepository users, PasswordEncoder passwords, SessionRegistry sessions) {
        this.users = users; this.passwords = passwords; this.sessions = sessions;
    }
    public Optional<User> optionalUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return Optional.empty();
        return Optional.of(requireUser(authentication));
    }
    public User requireUser(Authentication authentication) {
        if (authentication == null) throw new BadCredentialsException("Authentication required");
        return users.findByEmail(authentication.getName()).filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
    }
    public AccountProfile profile(Authentication authentication) { return view(requireUser(authentication)); }

    public Long customerForOrder(Authentication authentication, String contactEmail) {
        var actor = optionalUser(authentication);
        if (actor.isEmpty()) return null;
        User user = actor.get();
        if ("CUSTOMER".equals(user.getRole()) || user.getEmail().equalsIgnoreCase(contactEmail)) return user.getId();
        if (Set.of("SHOP_OWNER", "SALES_STAFF").contains(user.getRole()) && contactEmail != null) {
            return users.findByEmail(contactEmail.trim().toLowerCase(java.util.Locale.ROOT))
                    .filter(User::isActive).filter(customer -> "CUSTOMER".equals(customer.getRole()))
                    .map(User::getId).orElse(null);
        }
        return null;
    }

    @Transactional
    public AccountProfile updateProfile(Authentication authentication, String displayName, String phone) {
        User user = requireUser(authentication);
        user.setDisplayName(displayName.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        return view(users.save(user));
    }
    @Transactional
    public void changePassword(Authentication authentication, String currentPassword, String newPassword) {
        User user = requireUser(authentication);
        if (!passwords.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.");
        }
        if (currentPassword.equals(newPassword)) throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        user.setPasswordHash(passwords.encode(newPassword));
        users.save(user);
        expireSessions(user.getEmail());
    }
    public PageResponse<AccountProfile> employees(int page, int size, String query) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page or size");
        var result = users.searchEmployees(EMPLOYEE_ROLES, query == null ? "" : query.trim(), PageRequest.of(page, size, Sort.by("id").descending()));
        return new PageResponse<>(result.getContent().stream().map(AccountService::view).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public AccountProfile updateEmployee(long id, String displayName, String phone, String role, boolean active) {
        User user = users.findById(id).filter(u -> EMPLOYEE_ROLES.contains(u.getRole()))
                .orElseThrow(() -> new CatalogNotFoundException("Employee was not found"));
        if (!EMPLOYEE_ROLES.contains(role)) throw new IllegalArgumentException("Invalid employee role");
        boolean accessChanged = !user.getRole().equals(role) || user.isActive() != active;
        user.setDisplayName(displayName.trim());
        user.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        user.setRole(role); user.setActive(active);
        var result = view(users.save(user));
        if (accessChanged) expireSessions(user.getEmail());
        return result;
    }
    private void expireSessions(String email) {
        sessions.getAllSessions(email, false).forEach(session -> session.expireNow());
    }
    public static AccountProfile view(User user) {
        return new AccountProfile(user.getId(), user.getEmail(), user.getDisplayName(), user.getPhone(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
    public record AccountProfile(Long userId, String email, String displayName, String phone,
            String role, boolean active, OffsetDateTime createdAt) {}
}
