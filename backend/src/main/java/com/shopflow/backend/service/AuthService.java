package com.shopflow.backend.service;

import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(String email, String rawPassword, String displayName) {
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

    public User authenticateUser(String email, String rawPassword) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Email hoặc mật khẩu không đúng!"));
        if (!user.isActive()) {
            throw new RuntimeException("Email hoặc mật khẩu không đúng!");
        }
        boolean isMatch = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!isMatch) {
            throw new RuntimeException("Email hoặc mật khẩu không đúng!");
        }
        return user;
    }

    public Optional<User> findUserById(Long userId) {
        return userRepository.findById(userId);
    }
}