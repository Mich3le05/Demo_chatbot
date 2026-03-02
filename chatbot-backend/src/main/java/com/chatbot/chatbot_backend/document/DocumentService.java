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
import java.util.Arrays;
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

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    public DocumentResponse processFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.debug("Processing file via Tika Server: {}", filename);

        // FIX-3: check duplicato con query semantica sul filename,
        // non più con la query dummy "documento" che generava un embedding inutile
        if (isFileAlreadyIndexed(filename)) {
            throw new IllegalStateException(
                    "Il file '" + filename + "' è già presente in memoria. " +
                            "Rimuoverlo prima di caricarlo nuovamente."
            );
        }

        // 1. Estrazione testo via Tika
        String rawText = extractTextViaTika(file);

        // FIX-1: pulizia artefatti Word/PDF prima del chunking
        String extractedText = cleanTikaOutput(rawText);
        log.debug("Testo pulito: {} char (raw: {} char)", extractedText.length(), rawText.length());

        if (extractedText.isBlank()) {
            throw new IllegalStateException(
                    "Nessun testo estraibile dal file: " + filename
            );
        }

        // 2. Chunking semantico del testo
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

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIX-3: verifica se un file è già indicizzato usando il suo nome come query,
     * che è semanticamente più pertinente rispetto alla query dummy "documento".
     * In caso di errore ChromaDB, ritorna false per non bloccare l'upload.
     */
    private boolean isFileAlreadyIndexed(String filename) {
        try {
            List<Document> existing = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(filename)
                            .topK(1)
                            .similarityThreshold(0.0)
                            .filterExpression("source == '" + filename + "'")
                            .build()
            );
            return !existing.isEmpty();
        } catch (Exception e) {
            log.warn("Impossibile verificare duplicato per '{}': {}", filename, e.getMessage());
            return false; // in caso di errore, procedi con l'upload
        }
    }

    /**
     * Estrae il testo grezzo dal file tramite Apache Tika Server.
     */
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
     * FIX-1: rimuove gli artefatti prodotti da Tika sui file Word/PDF:
     *   - tag immagine:  [image: qualcosa]
     *   - bookmark Word: [bookmark: _Toc...]
     *   - watermark AI:  "AI-generated content may be incorrect."
     *   - righe vuote multiple e righe prive di contenuto alfanumerico
     *
     * Riduce il token count del prompt di circa il 30%, migliorando
     * la qualità del retrieval e la velocità del prompt processing.
     */
    private String cleanTikaOutput(String raw) {
        return raw
                // Rimuove tag immagine Tika (anche multiriga fino alla parentesi chiusa)
                .replaceAll("\\[image:[^]]*]", "")
                // Rimuove bookmark Word
                .replaceAll("\\[bookmark:[^]]*]", "")
                // Rimuove watermark AI di Word (con varianti di punteggiatura)
                .replaceAll("AI-generated content may be incorrect\\.?]?", "")
                // Collassa righe vuote eccessive in una sola riga vuota
                .replaceAll("\n{3,}", "\n\n")
                // Rimuove righe che non contengono alcun carattere alfanumerico
                .replaceAll("(?m)^[^a-zA-Z0-9àèìòùÀÈÌÒÙ\\s]*$", "")
                .strip();
    }

    /**
     * Chunking semantico a cascata:
     *   1. Divide per paragrafo (\n\n)
     *   2. Se un paragrafo supera chunkSize, lo divide per frasi (. ! ?)
     *   3. Aggrega le unità piccole in chunk fino a chunkSize con overlap semantico
     */
    private List<String> chunkText(String text) {
        String normalized = text
                .replaceAll("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();

        // Step 1: split per paragrafi
        List<String> paragraphs = Arrays.stream(normalized.split("\n\n"))
                .map(String::strip)
                .filter(p -> !p.isBlank())
                .toList();

        // Step 2: paragrafi troppo grandi → split per frasi
        List<String> sentences = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= chunkSize) {
                sentences.add(paragraph);
            } else {
                // Split su fine frase mantenendo il delimitatore
                String[] parts = paragraph.split("(?<=[.!?])\\s+");
                sentences.addAll(Arrays.asList(parts));
            }
        }

        // Step 3: aggrega frasi in chunk con overlap semantico
        return aggregateIntoChunks(sentences);
    }

    /**
     * Aggrega le unità di testo in chunk con overlap semantico basato
     * su frasi complete (non su caratteri arbitrari).
     */
    private List<String> aggregateIntoChunks(List<String> units) {
        List<String> chunks    = new ArrayList<>();
        StringBuilder current  = new StringBuilder();
        List<String> currentUnits = new ArrayList<>();

        for (String unit : units) {
            // Se aggiungere questa unità supera chunkSize, salva il chunk corrente
            if (current.length() + unit.length() > chunkSize && !current.isEmpty()) {
                String chunk = current.toString().strip();
                if (!chunk.isBlank()) {
                    chunks.add(chunk);
                }

                // Overlap semantico: riparti dall'ultima frase del chunk precedente
                current = new StringBuilder();
                int overlapStart = Math.max(0, currentUnits.size() - 1);
                for (int i = overlapStart; i < currentUnits.size(); i++) {
                    current.append(currentUnits.get(i)).append(" ");
                }
                currentUnits = new ArrayList<>(currentUnits.subList(overlapStart, currentUnits.size()));
            }

            current.append(unit).append(" ");
            currentUnits.add(unit);
        }

        // Aggiungi l'ultimo chunk residuo
        String last = current.toString().strip();
        if (!last.isBlank()) {
            chunks.add(last);
        }

        return chunks;
    }

    /**
     * Converte i chunk in Document objects con metadata e li salva in ChromaDB.
     * Spring AI calcola automaticamente gli embedding tramite LM Studio.
     */
    private void saveToVectorStore(List<String> chunks, String filename, String contentType) {
        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(
                        chunk,
                        Map.of(
                                "source",   filename,
                                "chunkId",  UUID.randomUUID().toString(),
                                "fileType", contentType != null ? contentType : "unknown"
                        )
                ))
                .toList();

        vectorStore.add(documents);
    }
}