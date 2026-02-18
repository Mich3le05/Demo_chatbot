// Hook per gestione stato chat

import { useState } from 'react'
import { chatService } from '../services/chatService'

export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

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

  const sendMessage = (userMessage, context = null) => {
    if (!userMessage.trim()) return

    setIsLoading(true)
    setError(null)

    addMessage(userMessage, 'user')

    const apiCall = context
      ? chatService.sendMessageWithContext(userMessage, context)
      : chatService.sendMessage(userMessage)

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
