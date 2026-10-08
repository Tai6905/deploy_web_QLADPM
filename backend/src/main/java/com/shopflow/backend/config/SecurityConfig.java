package com.shopflow.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    // Tiêu chí nghiệm thu: Sử dụng BCrypt (strength 10)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http
            // Tạm thời mở quyền cho các API Auth để test
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/orders", "/orders/*/payments").permitAll()
                .requestMatchers(HttpMethod.GET, "/orders/reference/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/categories/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.PUT, "/categories/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/categories/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.GET, "/orders/**", "/dashboard/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers("/inventory/**", "/receivings/**", "/deliveries/**")
                    .hasAnyRole("WAREHOUSE", "SHOP_OWNER")
                .requestMatchers(HttpMethod.GET, "/returns/orders")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.POST, "/returns/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.GET, "/returns/**")
                    .hasAnyRole("WAREHOUSE", "SHOP_OWNER")
                .requestMatchers(HttpMethod.PATCH, "/returns/**")
                    .hasAnyRole("WAREHOUSE", "SHOP_OWNER")
                .requestMatchers(HttpMethod.PATCH, "/orders/*/delivery")
                    .hasAnyRole("WAREHOUSE", "SHOP_OWNER")
                .requestMatchers("/v3/api-docs/**", "/docs/**", "/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )
            // Tiêu chí nghiệm thu: Bật CSRF và lưu token vào Cookie
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/register", "/api/auth/login") // Tạm bỏ qua CSRF khi test login
            )
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            // Tiêu chí nghiệm thu: Quản lý Session
            .sessionManagement(session -> session
                .sessionFixation().migrateSession() // Chống Session Fixation
                .maximumSessions(1) // Cho phép 1 thiết bị đăng nhập
            );
            
        return http.build();
    }
}