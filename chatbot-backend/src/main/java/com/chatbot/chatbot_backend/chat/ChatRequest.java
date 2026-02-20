package com.chatbot.chatbot_backend.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Il messaggio non può essere vuoto")
    private String message;

    private String context;

    // Nome del file su cui filtrare la ricerca RAG
    private String sourceFile;
}