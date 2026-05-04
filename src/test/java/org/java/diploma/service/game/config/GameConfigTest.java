package org.java.diploma.service.game.config;

import org.java.diploma.service.game.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GameConfigTest {

    @Test
    void buildsRestClientsForAuthBattleAndInternalAuth() {
        AuthServiceClientConfig auth = new AuthServiceClientConfig();
        BattleClientConfig battle = new BattleClientConfig();
        AuthInternalClientConfig authInternal = new AuthInternalClientConfig();

        assertThat(auth.authRestClient("http://auth:8080")).isNotNull();
        assertThat(battle.battleRestClient("http://battle:8080")).isNotNull();
        assertThat(authInternal.authInternalRestClient("http://auth:8080")).isNotNull();
    }

    @Test
    void buildsRedisTemplatesWithConnectionFactory() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory cf = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> redisTemplate = config.redisTemplate(cf);
        StringRedisTemplate stringTemplate = config.stringRedisTemplate(cf);

        assertThat(redisTemplate.getConnectionFactory()).isEqualTo(cf);
        assertThat(stringTemplate.getConnectionFactory()).isEqualTo(cf);
    }

    @Test
    void configuresCorsAndDisablesAutoFilterRegistration() {
        SecurityConfig securityConfig = new SecurityConfig();

        CorsConfigurationSource source = securityConfig.corsConfigurationSource("http://localhost:*, https://*.francecentral.cloudapp.azure.com");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/game/matches");
        assertThat(source).isNotNull();
        assertThat(source.getCorsConfiguration(req)).isNotNull();
        assertThat(source.getCorsConfiguration(req).getAllowedOriginPatterns())
                .contains("http://localhost:*", "https://*.francecentral.cloudapp.azure.com");

        JwtAuthenticationFilter filter = mock(JwtAuthenticationFilter.class);
        FilterRegistrationBean<JwtAuthenticationFilter> registration = securityConfig.disableJwtFilterAutoRegistration(filter);
        assertThat(registration.isEnabled()).isFalse();
    }
}
