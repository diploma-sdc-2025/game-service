package org.java.diploma.service.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MoveKingRequest(
        @NotNull @Min(0) @Max(7) Integer toX,
        @NotNull @Min(0) @Max(7) Integer toY
) {}
