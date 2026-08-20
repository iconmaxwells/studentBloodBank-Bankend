package com.bloodbank.bloodbank.config;

import lombok.RequiredArgsConstructor;
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
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.bloodbank.bloodbank.dto.common.ApiError;
import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.origin:http://localhost:5173}")
    private String corsOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register/donor",
                                "/api/v1/auth/refresh",
                                "/api/v1/health",
                                "/health"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/reference/**").permitAll()
                        .requestMatchers("/api/v1/swagger-ui/**", "/api/v1/docs/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/**").hasAnyRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/**").hasAnyRole("ADMIN")
                        .requestMatchers("/api/v1/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/api/v1/donor/**").hasAnyRole("DONOR", "ADMIN")
                        .requestMatchers("/api/v1/hospital/**").hasAnyRole("HOSPITAL", "ADMIN")
                        .requestMatchers("/api/v1/specialist/**").hasAnyRole("SPECIALIST", "STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/donors").hasAnyRole("STAFF", "ADMIN", "SPECIALIST")
                        .requestMatchers(HttpMethod.GET, "/api/v1/donors/**").hasAnyRole("STAFF", "ADMIN", "SPECIALIST", "DONOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/donors", "/api/v1/donors/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/donors", "/api/v1/donors/**").hasAnyRole("STAFF", "ADMIN", "DONOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/donors", "/api/v1/donors/**").hasAnyRole("ADMIN")
                        .requestMatchers("/api/v1/activity-logs/**").hasAnyRole("ADMIN")
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/requests/*/approve",
                                "/api/v1/requests/*/reject",
                                "/api/v1/requests/*/process",
                                "/api/v1/requests/*/complete"
                        ).hasAnyRole("STAFF", "ADMIN", "SPECIALIST")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(apiAccessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AccessDeniedHandler apiAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            ApiResponse<Void> body = ApiResponse.error(ApiError.builder()
                    .code("FORBIDDEN")
                    .message("Insufficient permissions for this resource")
                    .build());
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
        };
    }
}
