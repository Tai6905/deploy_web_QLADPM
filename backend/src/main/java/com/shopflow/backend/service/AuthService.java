package com.shopflow.backend.service;

import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.BadCredentialsException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(String email, String rawPassword, String displayName) {
        validatePassword(rawPassword);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || rawPassword == null || rawPassword.length() < 8
                || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Email, password (at least 8 characters), and display name are required");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setDisplayName(displayName.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        
        return userRepository.save(user);
    }

    public User createWarehouseEmployee(String email, String rawPassword, String displayName) {
        return createEmployee(email, rawPassword, displayName, "WAREHOUSE");
    }

    public User createEmployee(String email, String rawPassword, String displayName, String role) {
        if (!java.util.Set.of("WAREHOUSE", "SALES_STAFF").contains(role)) {
            throw new IllegalArgumentException("Employee role must be WAREHOUSE or SALES_STAFF");
        }
        validatePassword(rawPassword);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank() || rawPassword == null || rawPassword.length() < 8
                || rawPassword.length() > 72 || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Employee email, password (8-72 characters), and display name are required");
        }
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        User employee = new User();
        employee.setEmail(normalizedEmail);
        employee.setDisplayName(displayName.trim());
        employee.setPasswordHash(passwordEncoder.encode(rawPassword));
        employee.setRole(role);
        employee.setActive(true);
        return userRepository.save(employee);
    }

    public User authenticateUser(String email, String rawPassword) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid email or password");
        }
        boolean isMatch = rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!isMatch) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return user;
    }

    public Optional<User> findUserById(Long userId) {
        return userRepository.findById(userId);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must have at least 8 characters and at most 72 UTF-8 bytes");
        }
    }
}
