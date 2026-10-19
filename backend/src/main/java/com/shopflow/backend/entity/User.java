package com.shopflow.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash; // Sẽ chứa mã băm BCrypt

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(length = 20)
    private String phone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.OffsetDateTime createdAt;

    private String role = "CUSTOMER";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
