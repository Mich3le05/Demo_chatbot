package com.chatbot.chatbot_backend.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {

    //corpo della risposta: testo generato dal modello

    private String response;
    private boolean hasContext;
}