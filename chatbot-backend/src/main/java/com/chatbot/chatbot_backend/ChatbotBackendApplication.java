package com.chatbot.chatbot_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration;

@SpringBootApplication(exclude = {ChromaVectorStoreAutoConfiguration.class})
public class ChatbotBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotBackendApplication.class, args);
	}

}