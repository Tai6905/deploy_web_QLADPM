package com.shopflow.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void employeeIsCreatedWithWarehouseRoleAndEncodedPassword() {
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("temporary-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User employee = authService.createWarehouseEmployee(
                " Staff@Example.com ", "temporary-password", "  Store Staff  ");

        assertEquals("staff@example.com", employee.getEmail());
        assertEquals("Store Staff", employee.getDisplayName());
        assertEquals("WAREHOUSE", employee.getRole());
        assertEquals("encoded-password", employee.getPasswordHash());
        assertTrue(employee.isActive());
        verify(userRepository).findByEmail("staff@example.com");
    }

    @Test
    void duplicateEmployeeEmailIsRejected() {
        when(userRepository.findByEmail("staff@example.com"))
                .thenReturn(Optional.of(new User()));

        assertThrows(
                EmailAlreadyRegisteredException.class,
                () -> authService.createWarehouseEmployee(
                        " Staff@Example.com ", "temporary-password", "Store Staff"));
    }
}
