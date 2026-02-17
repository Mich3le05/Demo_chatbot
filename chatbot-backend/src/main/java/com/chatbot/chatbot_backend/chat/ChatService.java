package com.chatbot.chatbot_backend.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String sendMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public String sendMessageWithContext(String message, String context) {
        String prompt = """
                Hai a disposizione il seguente contesto estratto da un documento:
                
                %s
                
                Basandoti su questo contesto, rispondi alla seguente domanda:
                %s
                """.formatted(context, message);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}