package com.locallegalrag.dto;

public record CreateIngestJobRequest(Boolean resetIndex) {
    public boolean resolvedResetIndex() {
        return resetIndex == null || resetIndex;
    }
}
