import http from '../utils/httpClient'

export const chatService = {
  // ── Chiamate bloccanti (esistenti) ──────────────────────────────────────
  sendMessage: (message) => {
    return http.post('/chat/message', { message })
  },

  sendMessageWithContext: (message, context) => {
    return http.post('/chat/message', { message, context })
  },

  sendMessageWithRag: (message, sourceFile) => {
    return http.post('/chat/rag', { message, sourceFile })
  },

  // ── Streaming ────────────────────────────────────────────────────────────
  streamMessage: (message, onChunk, onDone, onError) => {
    fetch(`${import.meta.env.VITE_API_URL}/chat/message/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message }),
    })
      .then((res) => _consumeStream(res, onChunk, onDone))
      .catch(onError)
  },

  streamMessageWithRag: (message, sourceFile, onChunk, onDone, onError) => {
    fetch(`${import.meta.env.VITE_API_URL}/chat/rag/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, sourceFile }),
    })
      .then((res) => _consumeStream(res, onChunk, onDone))
      .catch(onError)
  },
}

// Legge il ReadableStream chunk per chunk e chiama onChunk ad ogni pezzo
async function _consumeStream(res, onChunk, onDone) {
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() // tieni l'ultima riga incompleta in buffer

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const chunk = line.slice(5) // non fare trim() per preservare gli spazi
        onChunk(chunk)
      }
    }
  }

  onDone()
}
