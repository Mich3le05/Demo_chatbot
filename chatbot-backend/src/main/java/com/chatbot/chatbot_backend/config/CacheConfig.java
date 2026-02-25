package com.chatbot.chatbot_backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("embeddings");
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(200)           // max 200 query cachate
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()              // per monitoraggio
        );
        return manager;
    }
}