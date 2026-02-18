// Service per comunicazione con il chatbot AI

import http from '../utils/httpClient'

export const chatService = {
  sendMessage: (message) => {
    return http.post('/chat/message', { message })
  },

  sendMessageWithContext: (message, context) => {
    return http.post('/chat/message', { message, context })
  },
}
