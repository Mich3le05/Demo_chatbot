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
import java.util.Arrays;

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

        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("documento")        // query dummy minima
                        .topK(1)
                        .similarityThreshold(0.0)  // prende qualsiasi risultato
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

    /**
     * Chunking semantico: rispetta i confini naturali del testo.
     * Strategia a cascata:
     *   1. Prova a dividere per paragrafo (\n\n)
     *   2. Se un paragrafo è troppo lungo, divide per frase (. ! ?)
     *   3. Aggrega i pezzi piccoli finché non raggiungono targetSize
     */
    private List<String> chunkText(String text) {
        // Normalizza gli a-capo multipli
        String normalized = text.replaceAll("\r\n", "\n")
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

        // Step 3: aggrega frasi piccole in chunk della dimensione target
        return aggregateIntoChunks(sentences);
    }

    /**
     * Aggrega le unità di testo in chunk con overlap semantico.
     * L'overlap è basato su frasi complete, non su caratteri.
     */
    private List<String> aggregateIntoChunks(List<String> units) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        List<String> currentUnits = new ArrayList<>(); // per gestire l'overlap

        for (String unit : units) {
            // Se aggiungere questa unità supera chunkSize, salva il chunk corrente
            if (current.length() + unit.length() > chunkSize && !current.isEmpty()) {
                String chunk = current.toString().strip();
                if (!chunk.isBlank()) {
                    chunks.add(chunk);
                }

                // Overlap semantico: riparti dall'ultima unità del chunk precedente
                // (non da un punto a caso nel mezzo)
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