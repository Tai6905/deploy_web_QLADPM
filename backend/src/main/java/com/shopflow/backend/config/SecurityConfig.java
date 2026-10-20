package com.shopflow.backend.config;

import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            SessionRegistry registry, CookieCsrfTokenRepository csrfRepository) {
        var concurrent = new ConcurrentSessionControlAuthenticationStrategy(registry);
        concurrent.setMaximumSessions(1);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent, new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(registry), new CsrfAuthenticationStrategy(csrfRepository)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "X-XSRF-TOKEN", "X-Correlation-ID"));
        cors.setExposedHeaders(List.of("X-Correlation-ID"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

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
            HttpSecurity http, SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfRepository, SessionRegistry registry,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // Tạm thời mở quyền cho các API Auth để test
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/account/**", "/api/account").authenticated()
                .requestMatchers(HttpMethod.GET, "/products/*/review-eligibility").authenticated()
                .requestMatchers(HttpMethod.PUT, "/products/*/reviews").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.PUT, "/products/*/content").hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/products/**").hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.POST, "/orders", "/orders/*/payments").permitAll()
                .requestMatchers(HttpMethod.GET, "/orders/reference/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/categories/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.PUT, "/categories/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/categories/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers(HttpMethod.GET, "/orders/**")
                    .hasAnyRole("SHOP_OWNER", "SALES_STAFF")
                .requestMatchers(HttpMethod.GET, "/dashboard/**").hasRole("SHOP_OWNER")
                .requestMatchers("/deliveries/**").hasAnyRole("WAREHOUSE", "SHOP_OWNER", "SALES_STAFF")
                .requestMatchers("/inventory/**", "/receivings/**")
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
                    .hasAnyRole("WAREHOUSE", "SHOP_OWNER", "SALES_STAFF")
                .requestMatchers("/api/employees", "/api/employees/**")
                    .hasRole("SHOP_OWNER")
                .requestMatchers("/v3/api-docs/**", "/docs", "/docs/**", "/swagger-ui/**", "/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )
            // Tiêu chí nghiệm thu: Bật CSRF và lưu token vào Cookie
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/auth/register", "/api/auth/login") // Tạm bỏ qua CSRF khi test login
            )
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Authentication required\",\"status\":401}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Access denied or invalid CSRF token\",\"status\":403}");
                }))
            // Tiêu chí nghiệm thu: Quản lý Session
            .sessionManagement(session -> session
                .sessionFixation().migrateSession() // Chống Session Fixation
                .maximumSessions(1)
                .sessionRegistry(registry)
                .expiredSessionStrategy(event -> {
                    event.getResponse().setStatus(401);
                    event.getResponse().setContentType("application/json");
                    event.getResponse().getWriter().write("{\"message\":\"Session expired; sign in again\",\"status\":401}");
                })
            );
            
        return http.build();
    }
}
