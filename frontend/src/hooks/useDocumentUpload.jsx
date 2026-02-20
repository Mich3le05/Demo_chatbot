// Hook per gestione upload documenti

import { useState } from 'react'
import { documentService } from '../services/documentService'
import { toast } from 'react-toastify'

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
        toast.success('Documento caricato con successo!')
      })
      .catch((err) => {
        setUploadError(err.message)
        toast.error(err.message)
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
