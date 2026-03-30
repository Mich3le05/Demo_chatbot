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

    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    /**
     * 10 scambi (user+assistant) = 20 messaggi totali.
     * DEVE essere pari per garantire integrità delle coppie.
     */
    private static final int MAX_MESSAGES = 20;

    public void addUserMessage(String sessionId, String text) {
        getOrCreate(sessionId).add(new UserMessage(text));
        trim(sessionId);
    }

    public void addAssistantMessage(String sessionId, String text) {
        getOrCreate(sessionId).add(new AssistantMessage(text));
    }

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

    /**
     * FIX: rimuove sempre 2 messaggi (1 coppia user/assistant) per mantenere
     * la coerenza della conversazione. Il vecchio codice rimuoveva 1 messaggio
     * alla volta, rischiando di lasciare un assistant senza il suo user.
     */
    private void trim(String sessionId) {
        List<Message> history = sessions.get(sessionId);
        while (history.size() > MAX_MESSAGES) {
            // Rimuove i 2 messaggi più vecchi (posizioni 0 e 0 dopo la prima rimozione)
            if (history.size() >= 2) {
                history.remove(0); // UserMessage
                history.remove(0); // AssistantMessage
            } else {
                history.remove(0);
            }
        }
    }
}