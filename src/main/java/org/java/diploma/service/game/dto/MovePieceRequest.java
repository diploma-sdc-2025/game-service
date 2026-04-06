package org.java.diploma.service.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MovePieceRequest(
        @NotNull @Min(0) @Max(7) Integer fromX,
        @NotNull @Min(0) @Max(7) Integer fromY,
        @NotNull @Min(0) @Max(7) Integer toX,
        @NotNull @Min(0) @Max(7) Integer toY
) {}
