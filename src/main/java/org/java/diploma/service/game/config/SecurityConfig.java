package org.java.diploma.service.game.config;

import org.java.diploma.service.game.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String ENDPOINT_ACTUATOR = "/actuator/**";
    private static final String ENDPOINT_API_DOCS = "/v3/api-docs/**";
    private static final String ENDPOINT_SWAGGER_UI = "/swagger-ui/**";
    private static final String ENDPOINT_SWAGGER_HTML = "/swagger-ui.html";
    private static final String ENDPOINT_CREATE_MATCH = "/api/game/matches";
    /** Read-only match metadata for matchmaking to validate Redis assignments (no JWT). */
    private static final String ENDPOINT_GET_MATCH_BY_ID = "/api/game/matches/*";

    private static final String SECURITY_FILTER_CHAIN_CONFIGURED = "Security filter chain configured for Game Service";
    private static final String CONFIGURING_SECURITY = "Configuring Game Service security";

    /**
     * Allows browser calls when VITE_GAME_URL points at this service (localhost:3000 → localhost:8083).
     * Same-origin via Vite proxy does not need CORS.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        c.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setExposedHeaders(List.of("*"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }

    /**
     * @Component on JwtAuthenticationFilter would otherwise auto-register it as a top-level
     * servlet filter, causing it to run twice (once outside the security chain, once inside).
     * OncePerRequestFilter dedupes per-request, but it's cleaner to keep it only inside the
     * security chain so request ordering is predictable.
     */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(JwtAuthenticationFilter f) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

        logger.info(CONFIGURING_SECURITY);

        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                ENDPOINT_ACTUATOR,
                                ENDPOINT_API_DOCS,
                                ENDPOINT_SWAGGER_UI,
                                ENDPOINT_SWAGGER_HTML,
                                ENDPOINT_CREATE_MATCH
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, ENDPOINT_GET_MATCH_BY_ID).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        logger.info(SECURITY_FILTER_CHAIN_CONFIGURED);
        return http.build();
    }
}