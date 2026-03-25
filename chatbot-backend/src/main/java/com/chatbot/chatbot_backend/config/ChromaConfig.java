package com.chatbot.chatbot_backend.config;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ChromaConfig {

    // Bean: client HTTP con timeout espliciti
    @Bean
    public RestClient.Builder chromaRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5s per connettersi
        factory.setReadTimeout(10000);    // 10s per leggere la risposta
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public ChromaApi chromaApi(RestClient.Builder chromaRestClientBuilder) {
        return ChromaApi.builder()
                .baseUrl("http://localhost:8000")
                .restClientBuilder(chromaRestClientBuilder)
                .build();
    }

    @Bean
    public ChromaVectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName("documents")
                .initializeSchema(true)
                .build();
    }
}