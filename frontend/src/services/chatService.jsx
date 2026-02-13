import http from '../utils/httpClient'

/**
 * Service per interazioni con il chatbot AI
 * Versione con Promises
 */
export const chatService = {
  /**
   * Invia messaggio al chatbot
   */
  sendMessage: (message) => {
    return http.post('/chat/message', { message })
  },

  /**
   * Carica e processa file (Excel/PDF)
   */
  uploadFile: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.postFile('/files/upload', formData)
  },

  /**
   * Invia messaggio con contesto file
   */
  sendMessageWithContext: (message, fileContext) => {
    return http.post('/chat/query', {
      message,
      context: fileContext,
    })
  },

  /**
   * Health check backend
   */
  healthCheck: () => {
    return http.get('/health')
  },
}
