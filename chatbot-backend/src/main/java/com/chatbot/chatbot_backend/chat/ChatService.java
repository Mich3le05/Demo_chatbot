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

    @Value("${app.chat.max-tokens:500}")
    private int maxTokens;

    @Value("${app.chat.max-tokens-simple:80}")
    private int maxTokensSimple;

    @Value("${app.rag.top-k:3}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.45}")
    private double similarityThreshold;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    /**
     * Istruzioni comportamentali nel system role.
     * Phi-3.5 rispetta queste direttive solo se arrivano come "system" message,
     * non se vengono inserite nel corpo del prompt utente.
     */
    private static final String SYSTEM_PROMPT =
            "Sei un assistente preciso e diretto. " +
                    "Quando viene fornito un CONTESTO, usalo come fonte primaria per rispondere. " +
                    "Integra il contesto con le tue conoscenze generali se necessario per dare una risposta completa. " +
                    "Usa titoli, sezioni ed elenchi puntati per strutturare la risposta in modo chiaro e leggibile. " +
                    "Non spiegare il tuo operato.";

    private static final String SYSTEM_GREETING =
            "Sei un assistente cordiale. Rispondi ai saluti in modo breve e naturale.";

    /**
     * Template RAG: struttura dati pura, zero istruzioni comportamentali.
     * Le istruzioni vanno nel system role, non qui.
     */
    private static final String RAG_TEMPLATE =
            "CONTESTO:\n%s\n\nDOMANDA: %s\nRISPOSTA:";

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    public String sendMessage(String message) {
        if (isGreeting(message)) {
            return chatClient.prompt()
                    .system(SYSTEM_GREETING)
                    .user(message)
                    .options(buildOptions(maxTokensSimple, 0.3))
                    .call()
                    .content();
        }
        List<Document> docs = searchRelevantDocs(message, null);
        if (!docs.isEmpty()) {
            log.info("Auto-RAG: '{}' → {} docs", message, docs.size());
            return sendMessageWithContext(message, buildContext(docs));
        }
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .options(buildOptions(maxTokensSimple, 0.3))
                .call()
                .content();
    }

    public String sendMessageWithRag(String message, String sourceFile) {
        List<Document> docs = searchRelevantDocs(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("RAG: nessun doc per '{}', fallback plain", message);
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .options(buildOptions(maxTokensSimple, 0.3))
                    .call()
                    .content();
        }
        log.info("RAG: '{}' (filter: {}) → {} docs", message, sourceFile, docs.size());
        return sendMessageWithContext(message, buildContext(docs));
    }

    public String sendMessageWithContext(String message, String context) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(RAG_TEMPLATE.formatted(context, message))
                .options(buildOptions(maxTokens, 0.1))
                .call()
                .content();
    }

    // ── Streaming ────────────────────────────────────────────────────────────

    public Flux<String> streamMessage(String message) {
        if (isGreeting(message)) {
            return chatClient.prompt()
                    .system(SYSTEM_GREETING)
                    .user(message)
                    .options(buildOptions(maxTokensSimple, 0.3))
                    .stream()
                    .content();
        }
        List<Document> docs = searchRelevantDocs(message, null);
        if (!docs.isEmpty()) {
            log.info("Auto-RAG stream: '{}' → {} docs", message, docs.size());
            return streamMessageWithContext(message, buildContext(docs));
        }
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .options(buildOptions(maxTokensSimple, 0.3))
                .stream()
                .content();
    }

    public Flux<String> streamMessageWithRag(String message, String sourceFile) {
        List<Document> docs = searchRelevantDocs(message, sourceFile);
        if (docs.isEmpty()) {
            log.warn("RAG stream: nessun doc per '{}', fallback plain", message);
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .options(buildOptions(maxTokensSimple, 0.3))
                    .stream()
                    .content();
        }
        log.info("RAG stream: '{}' → {} docs", message, docs.size());
        return streamMessageWithContext(message, buildContext(docs));
    }

    public Flux<String> streamMessageWithContext(String message, String context) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(RAG_TEMPLATE.formatted(context, message))
                .options(buildOptions(maxTokens, 0.1))
                .stream()
                .content();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private boolean isGreeting(String message) {
        if (message == null) return true;
        String lower = message.toLowerCase().strip();
        return lower.length() < 15 ||
                lower.matches("(ciao|salve|buongiorno|buonasera|hey|hi|hello|" +
                        "ok|grazie|thanks|prego|arrivederci|bye|saluti).*");
    }

    private List<Document> searchRelevantDocs(String message, String sourceFile) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(message)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (sourceFile != null && !sourceFile.isBlank()) {
            builder.filterExpression("source == '" + sanitize(sourceFile) + "'");
        }

        return vectorStore.similaritySearch(builder.build());
    }

    private String buildContext(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
    }

    private OpenAiChatOptions buildOptions(int tokens, double temperature) {
        return OpenAiChatOptions.builder()
                .maxTokens(tokens)
                .temperature(temperature)
                .build();
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }
}