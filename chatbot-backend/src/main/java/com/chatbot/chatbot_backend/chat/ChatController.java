package com.chatbot.chatbot_backend.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Chat semplice senza documenti.
     */
    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request) {
        String response;

        if (request.getContext() != null && !request.getContext().isBlank()) {
            response = chatService.sendMessageWithContext(request.getMessage(), request.getContext());
            return ResponseEntity.ok(new ChatResponse(response, true));
        }

        response = chatService.sendMessage(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(response, false));
    }

    /**
     * Chat RAG: cerca automaticamente nei documenti indicizzati.
     */
    @PostMapping("/rag")
    public ResponseEntity<ChatResponse> sendMessageWithRag(@Valid @RequestBody ChatRequest request) {
        String response = chatService.sendMessageWithRag(
                request.getMessage(),
                request.getSourceFile()
        );
        return ResponseEntity.ok(new ChatResponse(response, true));
    }
}