package com.chatbot.chatbot_backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
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

    @Value("${app.chat.max-tokens:600}")
    private int maxTokens;

    @Value("${app.chat.max-tokens-simple:150}")
    private int maxTokensSimple;

    @Value("${app.rag.top-k:4}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.38}")
    private double similarityThreshold;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
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
                       VectorStore vectorStore,
                       ConversationMemory conversationMemory) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.conversationMemory = conversationMemory;
    }

    // ── Non-streaming ─────────────────────────────────────────────────────────

    public String sendMessage(String message, String sessionId) {
        List<Document> docs = searchRelevantDocs(message, null);
        if (!docs.isEmpty()) {
            log.info("Auto-RAG sendMessage: '{}' → {} docs", message, docs.size());
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
        List<Document> docs = searchRelevantDocs(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("RAG: nessun doc per '{}', fallback plain", message);
            return sendMessage(message, sessionId);
        }
        log.info("RAG '{}' (filter: {}) → {} docs", message, sourceFile, docs.size());
        return sendMessageWithContext(message, buildContext(docs), sessionId);
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    public Flux<String> streamMessage(String message, String sessionId) {
        List<Document> docs = searchRelevantDocs(message, null);
        if (!docs.isEmpty()) {
            log.info("Auto-RAG stream: '{}' → {} docs", message, docs.size());
            return streamMessageWithContext(message, buildContext(docs), sessionId);
        }

        List<Message> history = getHistory(sessionId);
        // Accumula la risposta completa per salvarla in memoria
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
                .doOnComplete(() ->
                        saveToMemory(sessionId, message, accumulated.toString()));
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
                .doOnComplete(() ->
                        saveToMemory(sessionId, message, accumulated.toString()));
    }

    public Flux<String> streamMessageWithRag(String message, String sourceFile, String sessionId) {
        List<Document> docs = searchRelevantDocs(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("RAG stream: nessun doc per '{}', fallback plain", message);
            return streamMessage(message, sessionId);
        }
        log.info("RAG stream '{}' → {} docs", message, docs.size());
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

    private List<Document> searchRelevantDocs(String message, String sourceFile) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(message)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (sourceFile != null && !sourceFile.isBlank()) {
            String safeSource = sanitizeFilterValue(sourceFile);
            builder.filterExpression("source == '" + safeSource + "'");
        }

        return vectorStore.similaritySearch(builder.build());
    }

    private String buildContext(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    private String sanitizeFilterValue(String value) {
        if (value == null) return "";
        return value.replace("'", "\\'");
    }

    public void clearSession(String sessionId) {
        conversationMemory.clearSession(sessionId);
    }
}