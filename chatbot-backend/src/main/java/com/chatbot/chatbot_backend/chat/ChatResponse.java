package com.chatbot.chatbot_backend.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {

    private String response;
    private boolean hasContext;
}