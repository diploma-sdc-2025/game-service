package org.java.diploma.service.game.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateMatchRequest(
        @NotEmpty List<Long> playerIds
) {}
