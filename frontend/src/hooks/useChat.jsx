// frontend/src/hooks/useChat.jsx
import { useState, useRef } from 'react'
import { chatService } from '../services/chatService'

export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  const msgCounter = useRef(0)
  const abortControllerRef = useRef(null)

  // UUID generato una sola volta per tutta la sessione browser
  // Persiste tra i messaggi, si azzera solo al refresh della pagina
  const sessionId = useRef(crypto.randomUUID())

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

    // Notifica il backend di eliminare la memoria della sessione
    chatService.clearSession(sessionId.current)

    // Genera un nuovo sessionId: la prossima conversazione riparte da zero
    sessionId.current = crypto.randomUUID()

    setMessages([])
    setError(null)
    setIsLoading(false)
  }

  return { messages, isLoading, error, sendMessage, clearChat }
}
