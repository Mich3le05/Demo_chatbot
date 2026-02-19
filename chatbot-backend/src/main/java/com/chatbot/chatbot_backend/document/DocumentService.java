package com.chatbot.chatbot_backend.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DocumentService {

    @Value("${app.tika.server-url}")
    private String tikaServerUrl;

    @Value("${app.rag.chunk-size}")
    private int chunkSize;

    @Value("${app.rag.chunk-overlap}")
    private int chunkOverlap;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DocumentResponse processFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.debug("Processing file via Tika Server: {}", filename);

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

    /**
     * Divide il testo in chunk con overlap.
     * Esempio con chunkSize=1000 e chunkOverlap=200:
     * chunk1: caratteri 0-1000
     * chunk2: caratteri 800-1800
     * chunk3: caratteri 1600-2600
     */
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
}