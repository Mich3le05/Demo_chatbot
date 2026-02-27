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

    @Value("${app.chat.max-tokens:300}")
    private int maxTokens;

    @Value("${app.chat.max-tokens-simple:80}")
    private int maxTokensSimple;

    @Value("${app.rag.top-k:3}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.4}")
    private double similarityThreshold;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // Il prefisso fisso favorisce il KV Cache di LM Studio
    private static final String RAG_PROMPT_TEMPLATE =
            "CONTESTO:\n%s\n\nDOMANDA: %s\nRISPOSTA:";

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        // Nessun defaultSystem() → zero overhead KV Cache
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    public String sendMessage(String message) {
        List<Document> docs = searchRelevantDocs(message, null);
        if (!docs.isEmpty()) {
            log.info("Auto-RAG sendMessage: '{}' → {} docs", message, docs.size());
            return sendMessageWithContext(message, buildContext(docs));
        }
        return chatClient.prompt()
                .user(message)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokensSimple)
                        .temperature(0.3)
                        .build())
                .call()
                .content();
    }

    public String sendMessageWithRag(String message, String sourceFile) {
        List<Document> docs = searchRelevantDocs(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("RAG: nessun doc per '{}', fallback plain", message);
            return chatClient.prompt()
                    .user(message)
                    .options(OpenAiChatOptions.builder()
                            .maxTokens(maxTokensSimple)
                            .temperature(0.3)
                            .build())
                    .call()
                    .content();
        }
        log.info("RAG '{}' (filter: {}) → {} docs", message, sourceFile, docs.size());
        return sendMessageWithContext(message, buildContext(docs));
    }

    public String sendMessageWithContext(String message, String context) {
        return chatClient.prompt()
                .user(RAG_PROMPT_TEMPLATE.formatted(context, message))
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .temperature(0.1)
                        .build())
                .call()
                .content();
    }

    // Streaming

    public Flux<String> streamMessage(String message) {
        List<Document> docs = searchRelevantDocs(message, null);
        if (!docs.isEmpty()) {
            log.info("Auto-RAG stream: '{}' → {} docs", message, docs.size());
            return streamMessageWithContext(message, buildContext(docs));
        }
        return chatClient.prompt()
                .user(message)
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokensSimple)
                        .temperature(0.3)
                        .build())
                .stream()
                .content();
    }

    public Flux<String> streamMessageWithRag(String message, String sourceFile) {
        List<Document> docs = searchRelevantDocs(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("RAG stream: nessun doc per '{}', fallback plain", message);
            return chatClient.prompt()
                    .user(message)
                    .options(OpenAiChatOptions.builder()
                            .maxTokens(maxTokensSimple)
                            .temperature(0.3)
                            .build())
                    .stream()
                    .content();
        }
        log.info("RAG stream '{}' → {} docs", message, docs.size());
        return streamMessageWithContext(message, buildContext(docs));
    }

    public Flux<String> streamMessageWithContext(String message, String context) {
        return chatClient.prompt()
                .user(RAG_PROMPT_TEMPLATE.formatted(context, message))
                .options(OpenAiChatOptions.builder()
                        .maxTokens(maxTokens)
                        .temperature(0.1)
                        .build())
                .stream()
                .content();
    }

    // Helper

    private List<Document> searchRelevantDocs(String message, String sourceFile) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(message)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (sourceFile != null && !sourceFile.isBlank()) {
            builder.filterExpression("source == '" + sourceFile + "'");
        }

        return vectorStore.similaritySearch(builder.build());
    }

    private String buildContext(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }
}