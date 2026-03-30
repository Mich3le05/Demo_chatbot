package com.chatbot.chatbot_backend.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ChromaConfig {

    @Value("${app.chroma.base-url}")
    private String chromaBaseUrl;

    @Value("${app.chroma.collection-name}")
    private String collectionName;

    @Value("${app.chroma.connect-timeout-ms}")
    private int connectTimeout;

    @Value("${app.chroma.read-timeout-ms}")
    private int readTimeout;

    @Bean
    public RestClient.Builder chromaRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public ChromaApi chromaApi(RestClient.Builder chromaRestClientBuilder) {
        return ChromaApi.builder()
                .baseUrl(chromaBaseUrl)
                .restClientBuilder(chromaRestClientBuilder)
                .build();
    }

    @Bean
    public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(true)
                .build();
    }
}