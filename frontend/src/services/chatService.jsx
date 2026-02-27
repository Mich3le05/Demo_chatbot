import http from '../utils/httpClient'

const STREAM_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

export const chatService = {
  sendMessage: (message) => http.post('/chat/message', { message }),

  sendMessageWithRag: (message, sourceFile) =>
    http.post('/chat/rag', { message, sourceFile }),

  //Streaming
  streamMessage: (message, onChunk, onDone, onError) => {
    fetch(`${STREAM_BASE}/chat/message/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message }),
    })
      .then((res) => _consumeStream(res, onChunk, onDone))
      .catch(onError)
  },

  streamMessageWithRag: (message, sourceFile, onChunk, onDone, onError) => {
    fetch(`${STREAM_BASE}/chat/rag/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, sourceFile }),
    })
      .then((res) => _consumeStream(res, onChunk, onDone))
      .catch(onError)
  },
}

async function _consumeStream(res, onChunk, onDone) {
  if (!res.ok) {
    onDone()
    return
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop()
    for (const line of lines) {
      if (line.startsWith('data:')) {
        onChunk(line.slice(5))
      }
    }
  }
  onDone()
}
