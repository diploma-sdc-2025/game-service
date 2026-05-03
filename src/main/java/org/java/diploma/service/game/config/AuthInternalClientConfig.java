package org.java.diploma.service.game.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthInternalClientConfig {

    public static final String AUTH_INTERNAL_REST_CLIENT = "authInternalRestClient";

    @Bean
    @Qualifier(AUTH_INTERNAL_REST_CLIENT)
    RestClient authInternalRestClient(@Value("${diploma.auth-service.url:http://localhost:8080}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
