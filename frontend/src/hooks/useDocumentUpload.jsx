import { useState } from 'react'
import { documentService } from '../services/documentService'

/**
 * Hook per gestione upload documenti
 */
export const useDocumentUpload = () => {
  const [uploadedDocument, setUploadedDocument] = useState(null)
  const [isUploading, setIsUploading] = useState(false)
  const [uploadError, setUploadError] = useState(null)

  const uploadDocument = (file) => {
    setIsUploading(true)
    setUploadError(null)

    documentService
      .uploadDocument(file)
      .then((response) => {
        setUploadedDocument(response)
      })
      .catch((err) => {
        setUploadError(err.message)
      })
      .finally(() => {
        setIsUploading(false)
      })
  }

  const clearDocument = () => {
    setUploadedDocument(null)
    setUploadError(null)
  }

  return {
    uploadedDocument,
    isUploading,
    uploadError,
    uploadDocument,
    clearDocument,
  }
}
