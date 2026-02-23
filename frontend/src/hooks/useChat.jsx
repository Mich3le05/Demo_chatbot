// Hook per gestione stato chat / logica della chtat

import { useState } from 'react'
import { chatService } from '../services/chatService'

export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  // FUNZIONE: aggiunge messaggio all'array
  const addMessage = (text, sender) => {
    const newMessage = {
      id: Date.now(),
      text,
      sender,
      timestamp: new Date().toLocaleTimeString('it-IT', {
        hour: '2-digit',
        minute: '2-digit',
      }),
    }
    setMessages((prev) => [...prev, newMessage])
    return newMessage
  }

  // FUNZIONE PRINCIPALE: invia messaggio al backend
  const sendMessage = (
    userMessage,
    context = null,
    useRag = false,
    sourceFile = null,
  ) => {
    if (!userMessage.trim()) return

    setIsLoading(true)
    setError(null)
    addMessage(userMessage, 'user')

    let apiCall
    if (useRag) {
      apiCall = chatService.sendMessageWithRag(userMessage, sourceFile)
    } else if (context) {
      apiCall = chatService.sendMessageWithContext(userMessage, context)
    } else {
      apiCall = chatService.sendMessage(userMessage)
    }

    apiCall
      .then((response) => {
        addMessage(response.response, 'bot')
      })
      .catch((err) => {
        setError(err.message)
        addMessage('Errore: ' + err.message, 'system')
      })
      .finally(() => {
        setIsLoading(false)
      })
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
