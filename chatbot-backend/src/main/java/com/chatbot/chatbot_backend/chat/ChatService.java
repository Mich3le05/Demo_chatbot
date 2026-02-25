package com.chatbot.chatbot_backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    @Value("${app.chat.max-tokens:120}")
    private int maxTokens;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    // ── Chiamate bloccanti (esistenti) ──────────────────────────────────────

    public String sendMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .build())
                .call()
                .content();
    }

    public String sendMessageWithRag(String message, String sourceFile) {

        // Query enrichment: per riassunti usa una query più specifica
        String searchQuery = enrichSearchQuery(message);

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(searchQuery)          // ← query arricchita
                .topK(5)
                .similarityThreshold(0.3);   // ← scarta chunk irrilevanti (< 30% similarity)

        if (sourceFile != null && !sourceFile.isBlank()) {
            requestBuilder.filterExpression("source == '" + sourceFile + "'");
        }

        List<Document> relevantDocs = vectorStore.similaritySearch(requestBuilder.build());

        log.info("RAG search '{}' (filter: {}) → {} docs", message, sourceFile, relevantDocs.size());

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant docs found, falling back to plain chat for: '{}'", message);
            return sendMessage(message);
        }

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return sendMessageWithContext(message, context);
    }

    /**
     * Arricchisce query generiche per migliorare la similarity search.
     * "riassumi il documento" → cerca i contenuti principali, non la parola "riassumi"
     */
    private String enrichSearchQuery(String message) {
        String lower = message.toLowerCase().trim();

        if (lower.contains("riassumi") || lower.contains("riassunto") || lower.contains("di cosa parla")) {
            return "argomento principale contenuto obiettivo documento";
        }
        if (lower.contains("conclus")) {
            return "conclusione risultato finale sommario";
        }
        if (lower.contains("introduz") || lower.contains("inizia") || lower.contains("inizio")) {
            return "introduzione premessa contesto iniziale";
        }

        return message; // query specifica → usala as-is
    }

    public String sendMessageWithContext(String message, String context) {
        String minimalPrompt = String.format("CONTEXT:\n%s\n\nUSER QUESTION: %s", context, message);

        return chatClient.prompt()
                .user(minimalPrompt)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .build())
                .call()
                .content();
    }

    // ── Streaming ───────────────────────────────────────────────────────────

    public Flux<String> streamMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content();
    }

    public Flux<String> streamMessageWithRag(String message, String sourceFile) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(message)
                .topK(2);

        if (sourceFile != null && !sourceFile.isBlank()) {
            requestBuilder.filterExpression("source == '" + sourceFile + "'");
        }

        List<Document> relevantDocs = vectorStore.similaritySearch(requestBuilder.build());

        log.info("RAG stream search '{}' (filter: {}) → {} docs", message, sourceFile, relevantDocs.size());

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant docs found, falling back to plain stream for: '{}'", message);
            return streamMessage(message);
        }

        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return streamMessageWithContext(message, context);
    }

    public Flux<String> streamMessageWithContext(String message, String context) {
        String minimalPrompt = String.format("CONTEXT:\n%s\n\nUSER QUESTION: %s", context, message);

        return chatClient.prompt()
                .user(minimalPrompt)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content();
    }
}