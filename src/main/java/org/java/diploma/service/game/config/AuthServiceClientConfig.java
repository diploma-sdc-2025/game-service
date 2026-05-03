package org.java.diploma.service.game.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthServiceClientConfig {

    public static final String AUTH_REST_CLIENT = "authRestClient";

    @Bean
    @Qualifier(AUTH_REST_CLIENT)
    RestClient authRestClient(@Value("${diploma.auth-service.url:http://localhost:8080}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
