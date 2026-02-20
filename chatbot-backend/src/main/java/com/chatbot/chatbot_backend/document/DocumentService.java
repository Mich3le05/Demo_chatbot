package com.chatbot.chatbot_backend.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.vectorstore.SearchRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    @Value("${app.tika.server-url}")
    private String tikaServerUrl;

    @Value("${app.rag.chunk-size}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap}")
    private int chunkOverlap;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final VectorStore vectorStore;

    public DocumentResponse processFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.debug("Processing file via Tika Server: {}", filename);

        // 0. Verifica se il file è già presente in ChromaDB
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(filename)
                        .topK(1)
                        .filterExpression("source == '" + filename + "'")
                        .build()
        );

        if (!existing.isEmpty()) {
            throw new IllegalStateException(
                    "Il file '" + filename + "' è già presente in memoria. Rimuoverlo prima di caricarlo nuovamente."
            );
        }

        // 1. Estrazione testo via Tika
        String extractedText = extractTextViaTika(file);

        if (extractedText.isBlank()) {
            throw new IllegalStateException(
                    "Nessun testo estraibile dal file: " + filename
            );
        }

        // 2. Chunking del testo
        List<String> chunks = chunkText(extractedText);
        log.debug("Testo diviso in {} chunk", chunks.size());

        // 3. Salvataggio chunk in ChromaDB con embedding
        saveToVectorStore(chunks, filename, file.getContentType());
        log.debug("Salvati {} chunk in ChromaDB", chunks.size());

        return new DocumentResponse(
                filename,
                file.getContentType(),
                extractedText.trim(),
                chunks,
                true
        );
    }

    private String extractTextViaTika(MultipartFile file) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerUrl + "/tika"))
                .header("Accept", "text/plain")
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Tika Server error - status: " + response.statusCode()
                );
            }

            log.debug("Tika extraction OK: {}", file.getOriginalFilename());
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Richiesta a Tika Server interrotta", e);
        }
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).strip();

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end == text.length()) break;
            start += (chunkSize - chunkOverlap);
        }

        return chunks;
    }

    /**
     * Converte i chunk in Document objects con metadata
     * e li salva in ChromaDB. Spring AI calcola automaticamente
     * gli embedding tramite LM Studio.
     */
    private void saveToVectorStore(List<String> chunks, String filename, String contentType) {
        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(
                        chunk,
                        Map.of(
                                "source", filename,
                                "chunkId", UUID.randomUUID().toString(),
                                "fileType", contentType != null ? contentType : "unknown"
                        )
                ))
                .toList();

        vectorStore.add(documents);
    }
}