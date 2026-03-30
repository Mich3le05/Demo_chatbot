// frontend/src/hooks/useChat.jsx
import { useState, useRef } from 'react'
import { chatService } from '../services/chatService'

/**
 * FIX: crypto.randomUUID() è disponibile solo su HTTPS o localhost.
 * Su HTTP con IP di rete (es. 192.168.x.x) il browser la blocca.
 * Questo fallback genera un UUID v4 compatibile senza dipendenze esterne.
 */
function generateUUID() {
  if (
    typeof crypto !== 'undefined' &&
    typeof crypto.randomUUID === 'function'
  ) {
    return crypto.randomUUID()
  }
  // Fallback manuale UUID v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  const msgCounter = useRef(0)
  const abortControllerRef = useRef(null)

  const sessionId = useRef(generateUUID()) // usa il fallback sicuro

  const addMessage = (text, sender) => {
    const id = `${Date.now()}-${++msgCounter.current}`
    setMessages((prev) => [...prev, { id, text, sender }])
    return id
  }

  const appendToMessage = (id, chunk) => {
    setMessages((prev) =>
      prev.map((msg) =>
        msg.id === id ? { ...msg, text: msg.text + chunk } : msg,
      ),
    )
  }

  const sendMessage = (userMessage, useRag = false, sourceFile = null) => {
    if (!userMessage.trim()) return

    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }
    abortControllerRef.current = new AbortController()
    const { signal } = abortControllerRef.current

    setIsLoading(true)
    setError(null)
    addMessage(userMessage, 'user')
    const botMsgId = addMessage('', 'bot')

    const onChunk = (chunk) => {
      if (!signal.aborted) appendToMessage(botMsgId, chunk)
    }
    const onDone = () => {
      if (!signal.aborted) setIsLoading(false)
    }
    const onError = (err) => {
      if (err.name === 'AbortError') return
      setError(err.message)
      appendToMessage(botMsgId, 'Errore: ' + err.message)
      setIsLoading(false)
    }

    if (useRag) {
      chatService.streamMessageWithRag(
        userMessage,
        sourceFile,
        sessionId.current,
        onChunk,
        onDone,
        onError,
        signal,
      )
    } else {
      chatService.streamMessage(
        userMessage,
        sessionId.current,
        onChunk,
        onDone,
        onError,
        signal,
      )
    }
  }

  const clearChat = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }

    chatService.clearSession(sessionId.current)

    sessionId.current = generateUUID() // usa il fallback sicuro

    setMessages([])
    setError(null)
    setIsLoading(false)
  }

  return { messages, isLoading, error, sendMessage, clearChat }
}
