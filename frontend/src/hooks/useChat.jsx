import { useState } from 'react'
import { chatService } from '../services/chatService'

export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  let msgCounter = 0

  const addMessage = (text, sender) => {
    const newMessage = {
      id: `${Date.now()}-${++msgCounter}`,
      text,
      sender,
      timestamp: new Date().toLocaleTimeString('it-IT', {
        hour: '2-digit',
        minute: '2-digit',
      }),
    }
    setMessages((prev) => [...prev, newMessage])
    return newMessage.id
  }

  // Aggiorna il testo di un messaggio esistente tramite id
  const appendToMessage = (id, chunk) => {
    setMessages((prev) =>
      prev.map((msg) =>
        msg.id === id ? { ...msg, text: msg.text + chunk } : msg,
      ),
    )
  }

  const sendMessage = (userMessage, useRag = false, sourceFile = null) => {
    if (!userMessage.trim()) return

    setIsLoading(true)
    setError(null)
    addMessage(userMessage, 'user')

    // Crea messaggio bot vuoto e tieni l'id per aggiornarlo
    const botMsgId = addMessage('', 'bot')

    const onChunk = (chunk) => appendToMessage(botMsgId, chunk)
    const onDone = () => setIsLoading(false)
    const onError = (err) => {
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
      )
    } else {
      chatService.streamMessage(userMessage, onChunk, onDone, onError)
    }
  }

  const clearChat = () => {
    setMessages([])
    setError(null)
  }

  return {
    messages,
    isLoading,
    error,
    sendMessage,
    clearChat,
  }
}
