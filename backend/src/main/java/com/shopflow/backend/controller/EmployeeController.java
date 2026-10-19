package com.shopflow.backend.controller;

import com.shopflow.backend.entity.User;
import com.shopflow.backend.service.AuthService;
import com.shopflow.backend.service.AccountService;
import com.shopflow.backend.service.AccountService.AccountProfile;
import com.modernshopflow.catalog.api.PageResponse;
import com.shopflow.backend.service.EmailAlreadyRegisteredException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    private final AuthService authService;
    private final AccountService accounts;

    public EmployeeController(AuthService authService, AccountService accounts) {
        this.authService = authService;
        this.accounts = accounts;
    }

    @GetMapping public PageResponse<AccountProfile> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(name = "q", required = false) String query) {
        return accounts.employees(page, size, query);
    }
    @PatchMapping("/{id}") public AccountProfile update(@PathVariable long id, @Valid @RequestBody UpdateEmployee request) {
        return accounts.updateEmployee(id, request.displayName(), request.phone(), request.role(), request.active());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createWarehouseEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        try {
            User employee = authService.createEmployee(
                    request.email(), request.password(), request.displayName(), request.role() == null ? "WAREHOUSE" : request.role());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "userId", employee.getId(),
                    "username", employee.getEmail(),
                    "displayName", employee.getDisplayName(),
                    "role", employee.getRole()));
        } catch (EmailAlreadyRegisteredException | DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Email is already registered"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    public record CreateEmployeeRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 255) String displayName,
            @Pattern(regexp = "SALES_STAFF|WAREHOUSE") String role) {
    }
    public record UpdateEmployee(@NotBlank @Size(max = 255) String displayName, @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "SALES_STAFF|WAREHOUSE") String role, @NotNull Boolean active) {}
}
