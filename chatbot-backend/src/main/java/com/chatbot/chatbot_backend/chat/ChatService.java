package com.chatbot.chatbot_backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    /**
     * Chat semplice senza contesto documentale.
     */
    public String sendMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * RAG: cerca i chunk rilevanti in ChromaDB e li usa come contesto.
     */
    public String sendMessageWithRag(String message) {
        // 1. Similarity search su ChromaDB
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(5)
                        .build()
        );

        if (relevantDocs.isEmpty()) {
            log.debug("Nessun documento rilevante trovato per: {}", message);
            return sendMessage(message);
        }

        // 2. Costruzione contesto dai chunk recuperati
        String context = relevantDocs.stream()
                .map(doc -> String.format("[Fonte: %s]\n%s",
                        doc.getMetadata().getOrDefault("source", "unknown"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("RAG: trovati {} chunk rilevanti", relevantDocs.size());

        // 3. Invio a LM Studio con contesto
        return sendMessageWithContext(message, context);
    }

    /**
     * Chat con contesto passato manualmente (usato anche dal RAG).
     */
    public String sendMessageWithContext(String message, String context) {
        String prompt = """
                Hai a disposizione il seguente contesto estratto da un documento:
                
                %s
                
                Basandoti su questo contesto, rispondi alla seguente domanda:
                %s
                """.formatted(context, message);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}