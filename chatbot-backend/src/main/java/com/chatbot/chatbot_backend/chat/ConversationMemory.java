package com.chatbot.chatbot_backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ConversationMemory {

    // Ogni sessionId ha la propria storia di messaggi
    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    // Max scambi in memoria: 10 user + 10 assistant = 20 messaggi
    // Evita context overflow su Qwen2.5-3B con context 8192
    private static final int MAX_MESSAGES = 20;

    public void addUserMessage(String sessionId, String text) {
        getOrCreate(sessionId).add(new UserMessage(text));
        trim(sessionId);
    }

    public void addAssistantMessage(String sessionId, String text) {
        getOrCreate(sessionId).add(new AssistantMessage(text));
    }

    // Restituisce la storia SENZA l'ultimo messaggio utente
    // (quello viene aggiunto separatamente dal ChatService)
    public List<Message> getHistory(String sessionId) {
        return List.copyOf(getOrCreate(sessionId));
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.debug("Sessione eliminata: {}", sessionId);
    }

    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId) &&
                !sessions.get(sessionId).isEmpty();
    }

    private List<Message> getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    // Rimuove i messaggi più vecchi quando si supera il limite
    private void trim(String sessionId) {
        List<Message> history = sessions.get(sessionId);
        while (history.size() > MAX_MESSAGES) {
            history.remove(0);
        }
    }
}