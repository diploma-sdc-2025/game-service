package org.java.diploma.service.game.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BattleClientConfig {

    @Bean
    RestClient battleRestClient(@Value("${diploma.battle-service.url:http://localhost:8084}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
