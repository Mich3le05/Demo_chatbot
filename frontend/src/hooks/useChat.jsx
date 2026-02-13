import { useState } from 'react'
import { chatService } from '../services/chatService'

/**
 * Custom hook per gestione stato chat
 * Versione con Promises
 */
export const useChat = () => {
  const [messages, setMessages] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState(null)

  /**
   * Invia messaggio al chatbot
   */
  const sendMessage = (userMessage) => {
    setIsLoading(true)
    setError(null)

    // Aggiungi messaggio utente
    const userMsg = {
      id: Date.now(),
      text: userMessage,
      sender: 'user',
      timestamp: new Date(),
    }
    setMessages((prev) => [...prev, userMsg])

    // Chiamata API con .then/.catch
    chatService
      .sendMessage(userMessage)
      .then((response) => {
        // Aggiungi risposta bot
        const botMsg = {
          id: Date.now() + 1,
          text: response.response,
          sender: 'bot',
          timestamp: new Date(),
        }
        setMessages((prev) => [...prev, botMsg])
      })
      .catch((err) => {
        setError(err.message)

        // Messaggio errore visibile in chat
        const errorMsg = {
          id: Date.now() + 1,
          text: `Errore: ${err.message}`,
          sender: 'system',
          timestamp: new Date(),
        }
        setMessages((prev) => [...prev, errorMsg])
      })
      .finally(() => {
        setIsLoading(false)
      })
  }

  /**
   * Reset chat
   */
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
