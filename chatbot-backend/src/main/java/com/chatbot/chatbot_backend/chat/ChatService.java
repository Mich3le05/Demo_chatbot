package com.chatbot.chatbot_backend.chat;

import com.chatbot.chatbot_backend.document.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    @Value("${app.chat.max-tokens:600}")
    private int maxTokens;

    @Value("${app.chat.max-tokens-simple:150}")
    private int maxTokensSimple;

    private final ChatClient chatClient;
    private final DocumentService documentService; // usa searchWithRerank
    private final ConversationMemory conversationMemory;

    private static final String RAG_PROMPT_TEMPLATE =
            """
            Sei un assistente preciso. Rispondi SOLO usando le informazioni nel CONTESTO.
            Se l'informazione non è presente nel contesto, rispondi: "Non ho informazioni su questo."
            Non aggiungere spiegazioni sul tuo funzionamento. Rispondi in italiano.
            
            CONTESTO:
            %s
            
            DOMANDA: %s
            RISPOSTA:""";

    public ChatService(ChatClient.Builder builder,
                       DocumentService documentService,
                       ConversationMemory conversationMemory) {
        this.chatClient = builder.build();
        this.documentService = documentService;
        this.conversationMemory = conversationMemory;
    }

    // ── Non-streaming ─────────────────────────────────────────────────────────

    public String sendMessage(String message, String sessionId) {
        List<Document> docs = documentService.searchWithRerank(message, null);
        if (!docs.isEmpty()) {
            log.info("[{}] Auto-RAG: {} docs trovati", MDC.get("requestId"), docs.size());
            return sendMessageWithContext(message, buildContext(docs), sessionId);
        }
        List<Message> history = getHistory(sessionId);
        String response = chatClient.prompt()
                .messages(history)
                .user(message)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokensSimple)
                        .temperature(0.3)
                        .build())
                .call()
                .content();
        saveToMemory(sessionId, message, response);
        return response;
    }

    public String sendMessageWithContext(String message, String context, String sessionId) {
        List<Message> history = getHistory(sessionId);
        String response = chatClient.prompt()
                .messages(history)
                .user(RAG_PROMPT_TEMPLATE.formatted(context, message))
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .temperature(0.1)
                        .build())
                .call()
                .content();
        saveToMemory(sessionId, message, response);
        return response;
    }

    public String sendMessageWithRag(String message, String sourceFile, String sessionId) {
        List<Document> docs = documentService.searchWithRerank(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("[{}] RAG: nessun doc, fallback plain", MDC.get("requestId"));
            return sendMessage(message, sessionId);
        }
        return sendMessageWithContext(message, buildContext(docs), sessionId);
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    public Flux<String> streamMessage(String message, String sessionId) {
        List<Document> docs = documentService.searchWithRerank(message, null);
        if (!docs.isEmpty()) {
            return streamMessageWithContext(message, buildContext(docs), sessionId);
        }
        List<Message> history = getHistory(sessionId);
        StringBuilder accumulated = new StringBuilder();
        return chatClient.prompt()
                .messages(history)
                .user(message)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokensSimple)
                        .temperature(0.3)
                        .build())
                .stream()
                .content()
                .doOnNext(accumulated::append)
                .doOnComplete(() -> saveToMemory(sessionId, message, accumulated.toString()));
    }

    public Flux<String> streamMessageWithContext(String message, String context, String sessionId) {
        List<Message> history = getHistory(sessionId);
        StringBuilder accumulated = new StringBuilder();
        return chatClient.prompt()
                .messages(history)
                .user(RAG_PROMPT_TEMPLATE.formatted(context, message))
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .temperature(0.1)
                        .build())
                .stream()
                .content()
                .doOnNext(accumulated::append)
                .doOnComplete(() -> saveToMemory(sessionId, message, accumulated.toString()));
    }

    public Flux<String> streamMessageWithRag(String message, String sourceFile, String sessionId) {
        List<Document> docs = documentService.searchWithRerank(message, sourceFile);
        if (docs.isEmpty()) {
            return streamMessage(message, sessionId);
        }
        return streamMessageWithContext(message, buildContext(docs), sessionId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private List<Message> getHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        return conversationMemory.getHistory(sessionId);
    }

    private void saveToMemory(String sessionId, String userMessage, String assistantResponse) {
        if (sessionId == null || sessionId.isBlank()) return;
        conversationMemory.addUserMessage(sessionId, userMessage);
        conversationMemory.addAssistantMessage(sessionId, assistantResponse);
    }

    private String buildContext(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    // Arricchisce il contesto con i metadata di provenienza
                    Object idx = doc.getMetadata().get("chunkIndex");
                    Object src = doc.getMetadata().get("source");
                    String header = (src != null ? "[" + src + (idx != null ? " §" + idx : "") + "]\n" : "");
                    return header + doc.getText();
                })
                .collect(Collectors.joining("\n---\n"));
    }

    public void clearSession(String sessionId) {
        conversationMemory.clearSession(sessionId);
    }
}