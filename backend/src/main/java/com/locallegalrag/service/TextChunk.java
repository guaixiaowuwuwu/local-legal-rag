package com.locallegalrag.service;

import java.util.Map;

public record TextChunk(String content, Map<String, Object> metadata) {
}
