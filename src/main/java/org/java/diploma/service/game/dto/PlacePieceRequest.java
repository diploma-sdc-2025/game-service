package org.java.diploma.service.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlacePieceRequest(
        @NotNull @Min(0) @Max(7) Integer benchSlot,
        @NotNull @Min(0) @Max(7) Integer squareX,
        @NotNull @Min(0) @Max(7) Integer squareY
) {}
