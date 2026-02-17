package com.chatbot.chatbot_backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentResponse {

    private String fileName;
    private String fileType;
    private String extractedText;
    private int totalPages;
    private boolean success;
}