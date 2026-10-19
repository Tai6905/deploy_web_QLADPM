package com.shopflow.backend.repository;

import com.shopflow.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @Query("select u from User u where u.role in :roles and (lower(u.email) like lower(concat('%', :query, '%')) or lower(u.displayName) like lower(concat('%', :query, '%')))")
    Page<User> searchEmployees(@Param("roles") Collection<String> roles, @Param("query") String query, Pageable pageable);
}
