package com.chatbot.chatbot_backend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Conta le richieste per IP nell'ultimo minuto.
 * Capacità e soglia configurabili via properties.
 */
@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limit.chat.capacity:20}")
    private int capacity;

    @Value("${app.rate-limit.chat.refill-per-minute:20}")
    private int refillPerMinute;

    /**
     * Cache IP → contatore richieste. Si azzera automaticamente ogni minuto.
     */
    @Bean("rateLimitCache")
    public Cache<String, AtomicInteger> rateLimitCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    public int getRefillPerMinute() {
        return refillPerMinute;
    }
}