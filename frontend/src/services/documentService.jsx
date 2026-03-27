import http from '../utils/httpClient'

export const documentService = {
  uploadDocument: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.postFile('/document/upload', formData)
  },

  deleteDocument: (filename) =>
    http.delete(`/document/${encodeURIComponent(filename)}`),

  listDocuments: () => http.get('/document/list'),
}
