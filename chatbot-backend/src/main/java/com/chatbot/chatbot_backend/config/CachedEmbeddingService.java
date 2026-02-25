// CachedEmbeddingService.java
package com.chatbot.chatbot_backend.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CachedEmbeddingService {

    private final EmbeddingModel embeddingModel;

    // La query non ricalcola l'embedding → 0ms invece di 3s
    @Cacheable(value = "embeddings", key = "#query.toLowerCase().trim()")
    public float[] getEmbedding(String query) {
        log.debug("Cache MISS: calcolando embedding per '{}'", query);
        return embeddingModel.embed(query);
    }
}