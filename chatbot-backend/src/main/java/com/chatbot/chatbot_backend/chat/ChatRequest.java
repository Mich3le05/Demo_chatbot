package com.chatbot.chatbot_backend.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Il messaggio non può essere vuoto")
    @Size(max = 2000, message = "Il messaggio non può superare i 2000 caratteri")
    private String message;

    private String context;

    @Size(max = 500, message = "Il nome del file sorgente è troppo lungo")
    private String sourceFile;

    @Size(max = 36, message = "SessionId non valido") // UUID = 36 chars
    private String sessionId;
}