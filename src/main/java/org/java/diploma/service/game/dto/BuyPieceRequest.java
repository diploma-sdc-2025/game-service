package org.java.diploma.service.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BuyPieceRequest(
        @NotBlank String piece,
        @Min(0) @Max(7) Integer slot
) {}
