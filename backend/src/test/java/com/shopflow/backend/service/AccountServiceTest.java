package com.shopflow.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock SessionRegistry sessions;
    @InjectMocks AccountService service;
    User user(long id, String role, String email) {
        var user = new User(); user.setId(id); user.setRole(role); user.setEmail(email); return user;
    }
    UsernamePasswordAuthenticationToken authentication(String email, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    @Test void customerCannotAttachAnOrderToAnotherCustomersEmail() {
        when(users.findByEmail("self@example.com")).thenReturn(Optional.of(user(1, "CUSTOMER", "self@example.com")));
        assertEquals(1L, service.customerForOrder(authentication("self@example.com", "CUSTOMER"), "other@example.com"));
        verify(users, never()).findByEmail("other@example.com");
    }
    @Test void salesStaffCanRecordAPurchaseForAnExistingCustomer() {
        when(users.findByEmail("sales@example.com")).thenReturn(Optional.of(user(2, "SALES_STAFF", "sales@example.com")));
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(user(3, "CUSTOMER", "customer@example.com")));
        assertEquals(3L, service.customerForOrder(authentication("sales@example.com", "SALES_STAFF"), "customer@example.com"));
    }
    @Test void warehouseCannotAttachAPurchaseToSomeoneElse() {
        when(users.findByEmail("warehouse@example.com")).thenReturn(Optional.of(user(4, "WAREHOUSE", "warehouse@example.com")));
        assertNull(service.customerForOrder(authentication("warehouse@example.com", "WAREHOUSE"), "customer@example.com"));
        verify(users, never()).findByEmail("customer@example.com");
    }
}
