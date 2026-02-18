// Service per upload e parsing documenti Excel/PDF

import http from '../utils/httpClient'

export const documentService = {
  uploadDocument: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.postFile('/document/upload', formData)
  },
}
