package com.chatbot.chatbot_backend.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        String response;

        if (request.getContext() != null && !request.getContext().isBlank()) {
            response = chatService.sendMessageWithContext(
                    request.getMessage(), request.getContext(), sessionId);
            return ResponseEntity.ok(new ChatResponse(response, true));
        }

        response = chatService.sendMessage(request.getMessage(), sessionId);
        return ResponseEntity.ok(new ChatResponse(response, false));
    }

    @PostMapping("/rag")
    public ResponseEntity<ChatResponse> sendMessageWithRag(@Valid @RequestBody ChatRequest request) {
        String response = chatService.sendMessageWithRag(
                request.getMessage(),
                request.getSourceFile(),
                request.getSessionId());
        return ResponseEntity.ok(new ChatResponse(response, true));
    }

    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessage(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();

        if (request.getContext() != null && !request.getContext().isBlank()) {
            return chatService.streamMessageWithContext(
                    request.getMessage(), request.getContext(), sessionId);
        }
        return chatService.streamMessage(request.getMessage(), sessionId);
    }

    @PostMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessageWithRag(@Valid @RequestBody ChatRequest request) {
        return chatService.streamMessageWithRag(
                request.getMessage(),
                request.getSourceFile(),
                request.getSessionId());
    }

    // Endpoint per pulire la memoria server-side quando l'utente clicca "Pulisci chat"
    @PostMapping("/clear")
    public ResponseEntity<Void> clearSession(@Valid @RequestBody ChatRequest request) {
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            chatService.clearSession(request.getSessionId());
        }
        return ResponseEntity.noContent().build();
    }
}