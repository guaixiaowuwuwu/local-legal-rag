package com.locallegalrag.service;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final OpenAiCompatibleClient client;

    public ChatService(OpenAiCompatibleClient client) {
        this.client = client;
    }

    public String generate(String prompt) {
        return client.chat(prompt);
    }
}
