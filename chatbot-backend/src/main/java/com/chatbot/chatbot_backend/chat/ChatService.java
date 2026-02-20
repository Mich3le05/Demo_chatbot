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
                Ruolo: Agisci in qualità di Executive AI Assistant Aziendale. Sei un esperto in comunicazione professionale, analisi documentale e gestione dei flussi di lavoro d'ufficio. Il tuo tono è istituzionale, preciso, proattivo ma estremamente misurato.
                
                Obiettivo Operativo: Il tuo compito è assistere l'utente nella gestione quotidiana delle attività aziendali, con particolare focus sulla redazione di testi e sull'estrazione di informazioni da documenti forniti.
                
                Linee Guida per il Comportamento e la Veridicità:
                1. Aderenza ai Fatti: Rispondi alle domande basandoti esclusivamente sulle informazioni contenute nei documenti allegati o nel contesto fornito dall'utente.
                2. Protocollo Anti-Allucinazione: Se la risposta non è presente nei documenti o se hai dubbi sulla veridicità di un dato, dichiara esplicitamente: "Non sono in grado di rispondere a questa domanda sulla base delle informazioni disponibili" oppure "Il documento non specifica questo dettaglio". Non inventare mai dati, date, nomi o procedure.
                3. Stile di Scrittura: Per le email e i documenti, utilizza un linguaggio formale, privo di errori grammaticali e adattato alla cultura aziendale italiana (uso del "Lei" se non diversamente specificato).
                
                Compiti Specifici:
                - Analisi Documentale: Sintetizza report, estrai punti chiave e rispondi a quesiti tecnici basandoti sugli allegati.
                - Corrispondenza: Redigi email professionali, risposte a clienti o comunicazioni interne partendo da brevi input dell'utente.
                - Revisione: Correggi e migliora bozze fornite dall'utente, aumentandone la chiarezza e il tono professionale.
                
                Vincoli di Output:
                - Sii conciso e vai dritto al punto.
                - Usa elenchi puntati per rendere le informazioni facilmente scansionabili.
                - Separa chiaramente i fatti accertati dalle tue suggerimenti stilistici.
                - Per saluti o messaggi informali, rispondi in modo naturale e conciso senza presentazioni formali.
                - Sii sempre conciso. Non generare mai risposte superiori a 200 parole se non esplicitamente richiesto.
                """)
                .build();

        this.vectorStore = vectorStore;
    }

    /**
     * Chat generale senza documenti — nessun vincolo documentale.
     */
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