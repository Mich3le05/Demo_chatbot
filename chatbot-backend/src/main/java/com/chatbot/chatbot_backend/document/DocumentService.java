package com.chatbot.chatbot_backend.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class DocumentService {

    @Value("${app.tika.server-url}")
    private String tikaServerUrl;

    // HttpClient riusabile (thread-safe, creato una volta sola)
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DocumentResponse processFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.debug("Processing file via Tika Server: {}", filename);

        String extractedText = extractTextViaTika(file);

        if (extractedText.isBlank()) {
            throw new IllegalStateException(
                    "Nessun testo estraibile dal file: " + filename
            );
        }

        return new DocumentResponse(
                filename,
                file.getContentType(),
                extractedText.trim(),
                0,    // Tika Server non restituisce il numero di pagine, lo gestiamo dopo
                true
        );
    }

    /**
     * Invia il file al Tika Server tramite PUT /tika
     * e riceve il testo estratto come plain text.
     */
    private String extractTextViaTika(MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerUrl + "/tika"))
                .header("Accept", "text/plain")
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Tika Server ha risposto con status: " + response.statusCode()
                );
            }

            log.debug("Tika extraction successful for: {}", file.getOriginalFilename());
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Richiesta a Tika Server interrotta", e);
        }
    }
}