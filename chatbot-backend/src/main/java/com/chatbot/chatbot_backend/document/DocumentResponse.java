package com.chatbot.chatbot_backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DocumentResponse {

    private String fileName;
    private String fileType;
    private String extractedText;
    private List<String> chunks;
    private boolean success;
}