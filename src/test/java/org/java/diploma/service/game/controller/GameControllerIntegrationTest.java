package org.java.diploma.service.game.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.java.diploma.service.game.dto.CreateMatchRequest;
import org.java.diploma.service.game.dto.MatchResponse;
import org.java.diploma.service.game.service.GameService;
import org.java.diploma.service.game.service.GameStateRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
        import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean
    GameService gameService;
    @MockitoBean
    GameStateRedisService redisState;

    @Value("${auth.jwt.secret}")
    String jwtSecret;

    private String validJwt() {
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );

        return Jwts.builder()
                .subject("user-1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(key)
                .compact();
    }

    @Test
    void endpoints_requireAuth_byDefault() throws Exception {
        mvc.perform(get("/api/game/matches/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createMatch_validatesBody_whenAuthorized() throws Exception {
        var badJson = "{\"playerIds\":[]}";

        mvc.perform(post("/api/game/matches")
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMatch_returnsResponse_whenAuthorized_andValid() throws Exception {
        var req = new CreateMatchRequest(List.of(1L, 2L));
        var resp = new MatchResponse(100, "WAITING", 1, List.of(1L, 2L));

        when(gameService.createMatch(req)).thenReturn(resp);

        mvc.perform(post("/api/game/matches")
                        .header("Authorization", "Bearer " + validJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(100))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.currentRound").value(1))
                .andExpect(jsonPath("$.players[0]").value(1));
    }

}

