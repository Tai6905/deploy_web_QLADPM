package com.shopflow.backend.service;

import com.shopflow.backend.entity.User;
import com.shopflow.backend.repository.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates optional owner/warehouse accounts from deployment secrets; it never assigns default passwords. */
@Component
public class BootstrapRoleAccountsRunner implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String ownerEmail;
    private final String ownerPassword;
    private final String ownerName;
    private final String warehouseEmail;
    private final String warehousePassword;
    private final String warehouseName;

    public BootstrapRoleAccountsRunner(
            UserRepository users,
            PasswordEncoder passwords,
            @Value("${app.bootstrap.owner.email:}") String ownerEmail,
            @Value("${app.bootstrap.owner.password:}") String ownerPassword,
            @Value("${app.bootstrap.owner.display-name:Shop Owner}") String ownerName,
            @Value("${app.bootstrap.warehouse.email:}") String warehouseEmail,
            @Value("${app.bootstrap.warehouse.password:}") String warehousePassword,
            @Value("${app.bootstrap.warehouse.display-name:Warehouse Staff}") String warehouseName) {
        this.users = users;
        this.passwords = passwords;
        this.ownerEmail = ownerEmail;
        this.ownerPassword = ownerPassword;
        this.ownerName = ownerName;
        this.warehouseEmail = warehouseEmail;
        this.warehousePassword = warehousePassword;
        this.warehouseName = warehouseName;
    }

    @Override
    public void run(ApplicationArguments args) {
        createPrivilegedUser(ownerEmail, ownerPassword, ownerName, "SHOP_OWNER");
        createPrivilegedUser(warehouseEmail, warehousePassword, warehouseName, "WAREHOUSE");
    }

    private void createPrivilegedUser(String email, String password, String displayName, String role) {
        if (email == null || email.isBlank() || password == null || password.length() < 8) return;
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(normalizedEmail).isPresent()) return;
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwords.encode(password));
        user.setDisplayName(displayName.trim());
        user.setRole(role);
        users.save(user);
    }
}