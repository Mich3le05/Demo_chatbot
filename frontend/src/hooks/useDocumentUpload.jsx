// frontend/src/hooks/useDocumentUpload.jsx
import { useState, useEffect, useCallback } from 'react'
import { documentService } from '../services/documentService'
import { toast } from 'react-toastify'

export const useDocumentUpload = () => {
  const [uploadedDocument, setUploadedDocument] = useState(null)
  const [indexedDocuments, setIndexedDocuments] = useState([])
  const [isUploading, setIsUploading] = useState(false)
  const [uploadError, setUploadError] = useState(null)

  // FIX: useCallback stabilizza il riferimento della funzione tra i render,
  // così useEffect può includerla nelle dipendenze senza loop infiniti
  const refreshList = useCallback(() => {
    documentService
      .listDocuments()
      .then(setIndexedDocuments)
      .catch(() => setIndexedDocuments([]))
  }, []) // nessuna dipendenza esterna — documentService è stabile

  useEffect(() => {
    refreshList()
  }, [refreshList]) // ESLint è soddisfatto, nessun loop perché refreshList è stabile

  const uploadDocument = (file) => {
    setIsUploading(true)
    setUploadError(null)

    documentService
      .uploadDocument(file)
      .then((response) => {
        setUploadedDocument(response)
        toast.success('Documento caricato con successo!')
        refreshList()
      })
      .catch((err) => {
        setUploadError(err.message)
        toast.error(err.message)
      })
      .finally(() => setIsUploading(false))
  }

  const deleteDocument = (filename) => {
    documentService
      .deleteDocument(filename)
      .then(() => {
        if (uploadedDocument?.fileName === filename) {
          setUploadedDocument(null)
        }
        toast.success(`"${filename}" eliminato`)
        refreshList()
      })
      .catch((err) => toast.error('Errore eliminazione: ' + err.message))
  }

  const selectDocument = (filename) => {
    setUploadedDocument({ fileName: filename })
  }

  const clearDocument = () => {
    setUploadedDocument(null)
    setUploadError(null)
  }

  return {
    uploadedDocument,
    indexedDocuments,
    isUploading,
    uploadError,
    uploadDocument,
    deleteDocument,
    selectDocument,
    clearDocument,
  }
}
