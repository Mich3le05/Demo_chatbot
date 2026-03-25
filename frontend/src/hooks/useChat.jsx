import { useState, useRef } from 'react'
import { chatService } from '../services/chatService'

export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  const msgCounter = useRef(0)
  // Tiene traccia dell'AbortController dello stream corrente
  const abortControllerRef = useRef(null)

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

    // Se c'è già uno stream attivo, lo cancella prima di avviarne uno nuovo
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
    }
    // Crea un nuovo controller per questo stream
    abortControllerRef.current = new AbortController()
    const { signal } = abortControllerRef.current

    setIsLoading(true)
    setError(null)
    addMessage(userMessage, 'user')
    const botMsgId = addMessage('', 'bot')

    // I callback ignorano i chunk se lo stream è già stato cancellato
    const onChunk = (chunk) => {
      if (!signal.aborted) appendToMessage(botMsgId, chunk)
    }

    const onDone = () => {
      if (!signal.aborted) setIsLoading(false)
    }

    const onError = (err) => {
      // AbortError è normale (utente ha cancellato), non mostrare come errore
      if (err.name === 'AbortError') return
      setError(err.message)
      appendToMessage(botMsgId, 'Errore: ' + err.message)
      setIsLoading(false)
    }

    if (useRag) {
      chatService.streamMessageWithRag(
        userMessage,
        sourceFile,
        onChunk,
        onDone,
        onError,
        signal,
      )
    } else {
      chatService.streamMessage(userMessage, onChunk, onDone, onError, signal)
    }
  }

  const clearChat = () => {
    // Cancella lo stream in corso prima di svuotare la chat
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }
    setMessages([])
    setError(null)
    setIsLoading(false) // ← importante: resetta anche il loading
  }

  return { messages, isLoading, error, sendMessage, clearChat }
}
