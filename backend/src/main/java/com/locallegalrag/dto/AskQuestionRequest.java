package com.locallegalrag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(
        @NotBlank String question,
        @Min(1) @Max(20) Integer topK
) {
}
