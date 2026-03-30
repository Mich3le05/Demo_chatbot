package com.chatbot.chatbot_backend.health;

import com.chatbot.chatbot_backend.config.ChromaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    @Value("${spring.ai.openai.base-url}")
    private String lmStudioBaseUrl;

    @Value("${app.tika.server-url}")
    private String tikaServerUrl;

    private final VectorStore vectorStore;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());

        boolean lmOk = checkLmStudio();
        boolean tikaOk = checkTika();
        boolean chromaOk = checkChroma();

        response.put("lmStudio", lmOk ? "UP" : "DOWN");
        response.put("tika", tikaOk ? "UP" : "DOWN");
        response.put("chroma", chromaOk ? "UP" : "DOWN");

        boolean allUp = lmOk && tikaOk && chromaOk;
        response.put("status", allUp ? "UP" : "DEGRADED");

        // HTTP 200 se tutto ok, 503 se almeno un servizio è down
        return allUp
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }

    // ── Checks privati ────────────────────────────────────────────────────────

    /**
     * LM Studio espone GET /v1/models — risponde 200 se il server è attivo.
     */
    private boolean checkLmStudio() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(lmStudioBaseUrl + "/v1/models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            log.warn("LM Studio health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tika Server espone GET / — risponde 200 con la versione.
     */
    private boolean checkTika() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tikaServerUrl + "/tika"))
                    .header("Accept", "text/plain")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Tika health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * ChromaDB: esegue una similaritySearch minimale.
     * Se ChromaDB è down, vectorStore.similaritySearch() lancia eccezione.
     */
    private boolean checkChroma() {
        try {
            vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("health")
                            .topK(1)
                            .similarityThreshold(0.99) // soglia altissima = quasi nessun risultato
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.warn("Chroma health check failed: {}", e.getMessage());
            return false;
        }
    }
}