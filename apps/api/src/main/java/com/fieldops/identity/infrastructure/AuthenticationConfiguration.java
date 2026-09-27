package com.fieldops.identity.infrastructure;

import java.util.Map;
import java.nio.charset.StandardCharsets;

import com.fieldops.identity.application.UserService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class AuthenticationConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        var bcrypt = new BCryptPasswordEncoder(12) {
            @Override
            protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
                // Reject oversized login input instead of allowing BCrypt to throw
                // or treating a truncated prefix as the same password.
                return rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72
                        && super.matchesNonNull(rawPassword, encodedPassword);
            }
        };
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
    }

    @Bean
    SecurityFilterChain authenticationFilterChain(HttpSecurity http, PasswordUserDetailsService users,
                                                 PasswordEncoder encoder, UserService userService) throws Exception {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        http.authenticationProvider(provider)
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/onboarding").permitAll()
                        // Tenant endpoints additionally enforce context and roles in application services.
                        .anyRequest().authenticated())
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(form -> form.loginPage("/api/v1/auth/login")
                        .loginProcessingUrl("/api/v1/auth/login").usernameParameter("email")
                        .successHandler((request, response, authentication) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.setStatus(204);
                        })
                        .failureHandler((request, response, exception) -> {
                            var session = request.getSession(false);
                            if (session != null) session.invalidate();
                            AuthenticationErrors.unauthenticated(response);
                        }))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("FIELDOPS_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setHeader("Cache-Control", "no-store");
                            response.setStatus(204);
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> AuthenticationErrors.unauthenticated(response))
                        .accessDeniedHandler((request, response, exception) -> AuthenticationErrors.forbidden(response)))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .addFilterAfter(new ActiveUserSessionFilter(userService), SecurityContextHolderFilter.class);
        // Default CSRF protection includes login and logout.
        return http.build();
    }
}
