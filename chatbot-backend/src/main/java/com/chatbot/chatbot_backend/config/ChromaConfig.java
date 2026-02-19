package com.chatbot.chatbot_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Constructor;

@Configuration
public class ChromaConfig {

    @Bean
    public ChromaApi chromaApi() throws Exception {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8000")
                .requestFactory(new SimpleClientHttpRequestFactory());

        ObjectMapper objectMapper = new ObjectMapper();

        Constructor<ChromaApi> constructor = ChromaApi.class.getDeclaredConstructor(
                String.class, RestClient.Builder.class, ObjectMapper.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance("http://localhost:8000", builder, objectMapper);
    }

    @Bean
    public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName("documents")
                .initializeSchema(true)
                .build();
    }
}