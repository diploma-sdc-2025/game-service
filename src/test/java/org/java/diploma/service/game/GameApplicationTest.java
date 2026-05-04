package org.java.diploma.service.game;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class GameApplicationTest {

    @Test
    void mainDelegatesToSpringApplicationRun() {
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            GameApplication.main(new String[]{"--spring.profiles.active=test"});
            spring.verify(() -> SpringApplication.run(GameApplication.class, new String[]{"--spring.profiles.active=test"}));
        }
    }
}
