// frontend/src/services/chatService.jsx
import http from '../utils/httpClient'

const STREAM_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

export const chatService = {
  sendMessage: (message, sessionId) =>
    http.post('/chat/message', { message, sessionId }),

  sendMessageWithRag: (message, sourceFile, sessionId) =>
    http.post('/chat/rag', { message, sourceFile, sessionId }),

  clearSession: (sessionId) =>
    http.post('/chat/clear', { message: '_', sessionId }),

  streamMessage: (message, sessionId, onChunk, onDone, onError, signal) => {
    fetch(`${STREAM_BASE}/chat/message/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, sessionId }),
      signal,
    })
      .then((res) => _consumeStream(res, onChunk, onDone, onError))
      .catch(onError)
  },

  streamMessageWithRag: (
    message,
    sourceFile,
    sessionId,
    onChunk,
    onDone,
    onError,
    signal,
  ) => {
    fetch(`${STREAM_BASE}/chat/rag/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, sourceFile, sessionId }),
      signal,
    })
      .then((res) => _consumeStream(res, onChunk, onDone, onError))
      .catch(onError)
  },
}

/**
 * FIX: gestisce esplicitamente status HTTP != 200.
 * Prima il codice chiamava onDone() silenziosamente su errori HTTP,
 * lasciando il messaggio vuoto senza feedback all'utente.
 */
async function _consumeStream(res, onChunk, onDone, onError) {
  if (!res.ok) {
    // Prova a leggere il body dell'errore (JSON dal backend)
    try {
      const errorData = await res.json()
      onError(
        new Error(
          errorData.details || errorData.message || `Errore HTTP ${res.status}`,
        ),
      )
    } catch {
      onError(new Error(`Errore HTTP ${res.status}`))
    }
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() // l'ultima riga potrebbe essere incompleta
      for (const line of lines) {
        if (line.startsWith('data:')) onChunk(line.slice(5))
      }
    }
    // Processa eventuale residuo nel buffer
    if (buffer.startsWith('data:')) onChunk(buffer.slice(5))
  } catch (err) {
    onError(err)
    return
  }

  onDone()
}
