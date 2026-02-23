package com.chatbot.chatbot_backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatClient ragChatClient;
    private final VectorStore vectorStore;

    public ChatService(ChatClient.Builder builder, VectorStore vectorStore) {

        this.chatClient = builder.build();

        this.ragChatClient = builder
                .defaultSystem("""
                        Agisci come Executive AI Analyst & Business Writer. Sei un’interfaccia operativa di alto livello specializzata in efficienza aziendale, sintesi documentale e comunicazione istituzionale. Il tuo registro è asciutto, analitico e privo di elementi conversazionali superflui.
                        
                        Obiettivo:
                        Fornire supporto immediato nella gestione di flussi documentali e corrispondenza, garantendo precisione assoluta e aderenza ai fatti.
                        
                        Protocollo di Risposta (Vincoli Rigorosi):
                        
                        No Filler: Non usare saluti (es. "Certamente", "Ecco a te"), ringraziamenti o frasi di cortesia. Inizia direttamente con l'output richiesto.
                        
                        Aderenza ai Dati: Rispondi esclusivamente sulla base del materiale fornito. Se un'informazione manca, scrivi: "Dato non presente nel materiale di riferimento."
                        
                        Zero Allucinazioni: È vietata l'inferenza di dati non esplicitati (nomi, date, cifre).
                        
                        Stile: Italiano business formale. Uso della forma impersonale o del "Lei" (se rivolto a terzi). Sintassi paratattica (frasi brevi).
                        
                        Limite Quantitativo: Massimo 200 parole. Se il compito richiede più spazio, chiedi autorizzazione prima di procedere.
                        
                        Moduli Operativi:
                        
                        Analisi Documentale: Estrai esclusivamente: 1. Punti chiave, 2. Azioni richieste (Action Items), 3. Scadenze. Usa elenchi puntati.
                        
                        Corrispondenza: Redigi testi pronti all'invio partendo da input schematici. Struttura: Oggetto, Corpo (max 3 paragrafi), Chiusura.
                        
                        Revisione: Applica correzioni per eliminare ambiguità e migliorare il tono professionale. Restituisci il testo revisionato e, separatamente, una lista sintetica delle modifiche apportate.
                        
                        Formato Output:
                        
                        Usa il grassetto per evidenziare termini chiave o scadenze.
                        
                        Usa tabelle se devi confrontare dati estratti da più documenti.
                """)
                .build();

        this.vectorStore = vectorStore;
    }


    public String sendMessage(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    /**
     * RAG: cerca i chunk rilevanti in ChromaDB e li usa come contesto.
     * Usa il ragChatClient con system prompt documentale.
     */
    public String sendMessageWithRag(String message, String sourceFile) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(message)
                .topK(3);

        if (sourceFile != null && !sourceFile.isBlank()) {
            requestBuilder.filterExpression("source == '" + sourceFile + "'");
        }

        List<Document> relevantDocs = vectorStore.similaritySearch(requestBuilder.build());

        if (relevantDocs.isEmpty()) {
            log.debug("Nessun documento rilevante trovato per: {}", message);
            return sendMessage(message);
        }

        String context = relevantDocs.stream()
                .map(doc -> String.format("[Fonte: %s]\n%s",
                        doc.getMetadata().getOrDefault("source", "unknown"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("RAG: trovati {} chunk rilevanti", relevantDocs.size());

        return sendMessageWithContext(message, context);
    }

    /**
     * Chat con contesto documentale — usa il ragChatClient.
     */
    public String sendMessageWithContext(String message, String context) {
        String prompt = """
                Hai a disposizione il seguente contesto estratto da un documento:
                
                %s
                
                Basandoti su questo contesto, rispondi alla seguente domanda:
                %s
                """.formatted(context, message);

        return ragChatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}