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

    public String sendMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public String sendMessageWithRag(String message, String sourceFile) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(message)
                .topK(2);

        if (sourceFile != null && !sourceFile.isBlank()) {
            requestBuilder.filterExpression("source == '" + sourceFile + "'");
        }

        List<Document> relevantDocs = vectorStore.similaritySearch(requestBuilder.build());

        log.info("RAG search '{}' (filter: {}) → {} docs", message, sourceFile, relevantDocs.size());

        if (relevantDocs.isEmpty()) {
            return sendMessage(message);
        }


        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        return sendMessageWithContext(message, context);
    }

    public String sendMessageWithContext(String message, String context) {

        String minimalPrompt = String.format("CONTEXT:\n%s\n\nUSER QUESTION: %s", context, message);

        return chatClient.prompt()
                .user(minimalPrompt)
                .call()
                .content();
    }
}