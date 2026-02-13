# Chatbot AI - Spring Boot + React + LM Studio

Sistema di chatbot AI locale con supporto per analisi file Excel e PDF.

## 🏗️ Architettura

- **Backend**: Spring Boot 3.4.2 (Java 21) + Spring AI
- **Frontend**: React 18 + Vite + Bootstrap 5
- **AI Engine**: LM Studio (compatibile OpenAI API)

## 📁 Struttura Progetto

```
chatbot-ai-project/
├── backend/          # API REST Spring Boot
├── frontend/         # UI React con Vite
└── README.md
```

## 🚀 Setup Locale

### Prerequisiti

- **Java 21** (OpenJDK o Oracle)
- **Maven 3.6+**
- **Node.js 18+** e npm
- **LM Studio** installato e avviato

### 1. Backend Setup

```bash
cd backend

# Installa dipendenze e compila
./mvnw clean install

# Avvia il server
./mvnw spring-boot:run
```

Server disponibile su: **http://localhost:8080**

### 2. Frontend Setup

```bash
cd frontend

# Installa dipendenze
npm install

# Copia file configurazione
cp .env.example .env

# Avvia dev server
npm run dev
```

Applicazione disponibile su: **http://localhost:5173**

### 3. LM Studio Setup

1. Apri LM Studio
2. Carica un modello (es. Llama 3, Mistral)
3. Avvia il server locale (default: `http://localhost:1234`)

## ⚙️ Configurazione

### Variabili d'Ambiente Frontend

Crea `frontend/.env`:

```env
VITE_API_URL=http://localhost:8080/api
```

### Configurazione Backend

File `backend/src/main/resources/application.yml`:

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:1234/v1
      api-key: lm-studio
```

## 🧪 Test Connessione

1. Avvia Backend: `http://localhost:8080`
2. Avvia Frontend: `http://localhost:5173`
3. Clicca su "Test Connessione Backend"
4. Verifica risposta positiva

## 📦 Tecnologie Utilizzate

### Backend

- Spring Boot 3.4.2
- Spring AI OpenAI Starter
- Apache POI (Excel parsing)
- Apache PDFBox (PDF parsing)
- Lombok

### Frontend

- React 18
- Vite 5
- Bootstrap 5 + React Bootstrap
- Fetch API

## 🤝 Contribuire

1. Fork del progetto
2. Crea un branch feature (`git checkout -b feature/NuovaFunzionalita`)
3. Commit delle modifiche (`git commit -m 'Aggiunta nuova funzionalità'`)
4. Push al branch (`git push origin feature/NuovaFunzionalita`)
5. Apri una Pull Request

## 📝 Licenza

MIT License

## 👤 Autore

Michele Mandanici(https://github.com/Mich3le05)
