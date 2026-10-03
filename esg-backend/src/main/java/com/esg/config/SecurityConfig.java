package com.esg.config;

import com.esg.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Value("${esg.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints; each is marked @PublicEndpoint (see ControllerSecurityArchTest).
                .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/logout",
                        "/v1/auth/mfa/setup", "/v1/auth/mfa/verify").permitAll()
                .requestMatchers("/v1/api-docs/**", "/v1/swagger-ui.html", "/v1/swagger-ui/**").permitAll()
                // WP-34: the anonymous whistleblower channel (submit, status) needs no sign-in (TRD §6.5.5).
                .requestMatchers(HttpMethod.POST, "/v1/public/whistleblower/*", "/v1/public/whistleblower/*/status").permitAll()
                // WP-31: a supplier answers a questionnaire through its tokenised link, with no account.
                .requestMatchers(HttpMethod.GET, "/v1/public/supplier-questionnaires/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/public/certificates/*").permitAll()
                .requestMatchers(HttpMethod.PUT, "/v1/public/supplier-questionnaires/*/answers").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/public/supplier-questionnaires/*/evidence", "/v1/public/supplier-questionnaires/*/submit").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> writeError(request, response,
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "ESG-401", "Sign in to continue"))
                .accessDeniedHandler((request, response, e) -> writeError(request, response,
                    org.springframework.http.HttpStatus.FORBIDDEN, "ESG-403", "You do not have permission to perform this action")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                                   org.springframework.http.HttpStatus status, String code, String detail) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.getWriter().write(com.esg.exception.Problems.json(status, code, detail, request.getRequestURI()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
