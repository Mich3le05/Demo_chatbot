package com.chatbot.chatbot_backend.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

    @Value("${app.rag.top-k:4}")
    private int topK;

    @Value("${app.rag.similarity-threshold:0.38}")
    private double similarityThreshold;

    @Value("${app.rag.rerank-top-n:3}")
    private int rerankTopN;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final VectorStore vectorStore;

    // ─────────────────────────────────────────────────────────────────────────

    public DocumentResponse processFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String requestId = MDC.get("requestId"); // tracing
        log.info("[{}] Processing file: {}", requestId, filename);

        if (isFileAlreadyIndexed(filename)) {
            throw new IllegalStateException(
                    "Il file '" + filename + "' è già presente in memoria. " +
                            "Rimuoverlo prima di caricarlo nuovamente.");
        }

        String rawText = extractTextViaTika(file);
        String extractedText = cleanTikaOutput(rawText);
        log.info("[{}] Testo estratto: {} char (raw: {})", requestId, extractedText.length(), rawText.length());

        if (extractedText.isBlank()) {
            throw new IllegalStateException("Nessun testo estraibile dal file: " + filename);
        }

        List<String> chunks = chunkText(extractedText);
        log.info("[{}] Chunk generati: {}", requestId, chunks.size());

        saveToVectorStore(chunks, filename, file.getContentType());
        log.info("[{}] Salvati {} chunk in ChromaDB per '{}'", requestId, chunks.size(), filename);

        return new DocumentResponse(filename, file.getContentType(), extractedText.trim(), chunks, true);
    }

    // ── Ricerca con reranking ─────────────────────────────────────────────────

    /**
     * Esegue similaritySearch e applica un reranking leggero basato sulla
     * frequenza dei termini della query nel testo del chunk (TF semplificato).
     * Migliora la precisione senza richiedere un secondo modello.
     */
    public List<Document> searchWithRerank(String query, String sourceFile) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        if (sourceFile != null && !sourceFile.isBlank()) {
            builder.filterExpression("source == '" + sanitizeFilterValue(sourceFile) + "'");
        }

        List<Document> candidates = vectorStore.similaritySearch(builder.build());
        if (candidates.isEmpty()) return candidates;

        // Reranking: punteggio = similarityScore * (1 + termFrequencyBonus)
        String[] queryTerms = query.toLowerCase().split("\\s+");
        List<Document> reranked = candidates.stream()
                .sorted(Comparator.comparingDouble(doc -> -rerankScore(doc, queryTerms)))
                .limit(rerankTopN)
                .collect(Collectors.toList());

        log.debug("Rerank: {} candidati → {} selezionati", candidates.size(), reranked.size());
        return reranked;
    }

    private double rerankScore(Document doc, String[] queryTerms) {
        String text = doc.getText().toLowerCase();
        // Similarity score già calcolato da Chroma (se disponibile nei metadata)
        double base = 1.0;
        Object scoreObj = doc.getMetadata().get("distance");
        if (scoreObj instanceof Number) {
            base = 1.0 - ((Number) scoreObj).doubleValue(); // Chroma usa distanza, non similarità
        }

        long termMatches = Arrays.stream(queryTerms)
                .filter(term -> term.length() > 3) // ignora stop-words brevi
                .filter(text::contains)
                .count();

        double tfBonus = (double) termMatches / Math.max(queryTerms.length, 1);
        return base * (1.0 + 0.3 * tfBonus);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isFileAlreadyIndexed(String filename) {
        try {
            List<Document> existing = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(filename)
                            .topK(1)
                            .similarityThreshold(0.0)
                            .filterExpression("source == '" + sanitizeFilterValue(filename) + "'")
                            .build());
            return !existing.isEmpty();
        } catch (Exception e) {
            log.warn("Impossibile verificare duplicato per '{}': {}", filename, e.getMessage());
            return false;
        }
    }

    private String extractTextViaTika(MultipartFile file) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tikaServerUrl + "/tika"))
                .header("Accept", "text/plain")
                .header("Content-Type", "application/octet-stream")
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Tika Server error - status: " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Richiesta a Tika Server interrotta", e);
        }
    }

    private String cleanTikaOutput(String raw) {
        return raw
                .replaceAll("\\[image:[^]]*]", "")
                .replaceAll("\\[bookmark:[^]]*]", "")
                .replaceAll("AI-generated content may be incorrect\\.?]?", "")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("(?m)^[^a-zA-Z0-9àèìòùÀÈÌÒÙ\\s]*$", "")
                .strip();
    }

    private List<String> chunkText(String text) {
        String normalized = text.replaceAll("\r\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
        List<String> paragraphs = Arrays.stream(normalized.split("\n\n"))
                .map(String::strip)
                .filter(p -> !p.isBlank())
                .toList();

        List<String> sentences = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= chunkSize) {
                sentences.add(paragraph);
            } else {
                String[] parts = paragraph.split("(?<=[.!?])\\s+");
                sentences.addAll(Arrays.asList(parts));
            }
        }
        return aggregateIntoChunks(sentences);
    }

    private List<String> aggregateIntoChunks(List<String> units) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        List<String> currentUnits = new ArrayList<>();

        for (String unit : units) {
            boolean wouldExceed = (current.length() + unit.length() + 1) > chunkSize;
            if (wouldExceed && !current.isEmpty()) {
                String chunk = current.toString().strip();
                if (!chunk.isBlank()) chunks.add(chunk);

                int overlapChars = 0;
                int overlapStart = currentUnits.size();
                for (int i = currentUnits.size() - 1; i >= 0; i--) {
                    overlapChars += currentUnits.get(i).length();
                    overlapStart = i;
                    if (overlapChars >= chunkOverlap) break;
                }
                current = new StringBuilder();
                for (int i = overlapStart; i < currentUnits.size(); i++) {
                    current.append(currentUnits.get(i)).append(" ");
                }
                currentUnits = new ArrayList<>(currentUnits.subList(overlapStart, currentUnits.size()));
            }
            current.append(unit).append(" ");
            currentUnits.add(unit);
        }

        String last = current.toString().strip();
        if (!last.isBlank()) chunks.add(last);
        return chunks;
    }

    /**
     * Metadata arricchiti: aggiunge chunkIndex, totalChunks, uploadedAt.
     * Permette di filtrare per posizione e di mostrare la provenienza nella UI.
     */
    private void saveToVectorStore(List<String> chunks, String filename, String contentType) {
        String uploadedAt = Instant.now().toString();
        int total = chunks.size();

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source",       filename);
            metadata.put("chunkId",      UUID.randomUUID().toString());
            metadata.put("chunkIndex",   i);                    // posizione nel documento
            metadata.put("totalChunks",  total);               // totale chunk del file
            metadata.put("fileType",     contentType != null ? contentType : "unknown");
            metadata.put("uploadedAt",   uploadedAt);          // timestamp upload

            documents.add(new Document(chunks.get(i), metadata));
        }

        vectorStore.add(documents);
    }

    /**
     * Fix sicurezza: escape sia apice singolo che backslash.
     */
    private String sanitizeFilterValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    public void deleteDocument(String filename) {
        String requestId = MDC.get("requestId");
        try {
            List<Document> toDelete = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(filename)
                            .topK(1000)
                            .similarityThreshold(0.0)
                            .filterExpression("source == '" + sanitizeFilterValue(filename) + "'")
                            .build());

            if (toDelete.isEmpty()) {
                log.warn("[{}] Nessun chunk trovato per: {}", requestId, filename);
                return;
            }

            List<String> ids = toDelete.stream().map(Document::getId).toList();
            vectorStore.delete(ids);
            log.info("[{}] Eliminati {} chunk per: {}", requestId, ids.size(), filename);

        } catch (Exception e) {
            log.error("[{}] Errore eliminazione '{}': {}", requestId, filename, e.getMessage());
            throw new IllegalStateException("Impossibile eliminare il documento: " + filename, e);
        }
    }

    public List<String> getIndexedDocuments() {
        try {
            // FIX: usa topK alto + similarityThreshold 0.0 per recuperare tutti i chunk
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("a") // query minima, vogliamo tutti i doc
                            .topK(500)
                            .similarityThreshold(0.0)
                            .build());

            return docs.stream()
                    .map(doc -> (String) doc.getMetadata().get("source"))
                    .filter(source -> source != null && !source.isBlank())
                    .distinct()
                    .sorted()
                    .toList();

        } catch (Exception e) {
            log.error("Errore recupero lista documenti: {}", e.getMessage());
            return List.of();
        }
    }
}