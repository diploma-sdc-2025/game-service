package org.java.diploma.service.game.redis;

import org.java.diploma.service.game.service.GameStateRedisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GameStateRedisServiceIT {

    private static redis.embedded.RedisServer redisServer;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new redis.embedded.RedisServer(6379);
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() {
        redisServer.stop();
    }

    @Autowired
    GameStateRedisService redisService;

    @Test
    void initMatchState_andReadBack() {
        redisService.initMatchState(1);

        assertThat(redisService.getState(1)).isNotNull();
        assertThat(redisService.getBoard(1)).isNotNull();
    }
}


