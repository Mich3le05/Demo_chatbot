/**
 * HTTP Client basato su Fetch API nativa
 * Gestisce comunicazione con backend Spring Boot
 * Versione con Promises (.then/.catch)
 */

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

/**
 * Wrapper generico per fetch con gestione errori
 */
const httpClient = (endpoint, options = {}) => {
  const config = {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  }

  return fetch(`${BASE_URL}${endpoint}`, config)
    .then((response) => {
      // Gestione errori HTTP
      if (!response.ok) {
        return response
          .json()
          .catch(() => ({ message: 'Errore di comunicazione con il server' }))
          .then((errorData) => {
            throw new Error(errorData.message || `HTTP ${response.status}`)
          })
      }

      // Parse JSON se presente
      const contentType = response.headers.get('content-type')
      if (contentType && contentType.includes('application/json')) {
        return response.json()
      }

      return null
    })
    .catch((error) => {
      console.error(`[API Error] ${endpoint}:`, error)
      throw error
    })
}

/**
 * Metodi HTTP
 */
export const http = {
  get: (endpoint, options = {}) => {
    return httpClient(endpoint, { method: 'GET', ...options })
  },

  post: (endpoint, data, options = {}) => {
    return httpClient(endpoint, {
      method: 'POST',
      body: JSON.stringify(data),
      ...options,
    })
  },

  postFile: (endpoint, formData, options = {}) => {
    return httpClient(endpoint, {
      method: 'POST',
      body: formData,
      headers: {}, // Nessun Content-Type, il browser lo gestisce
      ...options,
    })
  },

  put: (endpoint, data, options = {}) => {
    return httpClient(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data),
      ...options,
    })
  },

  delete: (endpoint, options = {}) => {
    return httpClient(endpoint, { method: 'DELETE', ...options })
  },
}

export default http
