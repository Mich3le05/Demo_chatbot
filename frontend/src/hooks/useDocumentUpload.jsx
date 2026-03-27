import { useState, useEffect } from 'react'
import { documentService } from '../services/documentService'
import { toast } from 'react-toastify'

export const useDocumentUpload = () => {
  const [uploadedDocument, setUploadedDocument] = useState(null)
  const [indexedDocuments, setIndexedDocuments] = useState([])
  const [isUploading, setIsUploading] = useState(false)
  const [uploadError, setUploadError] = useState(null)

  // Carica la lista dei documenti già indicizzati all'avvio
  useEffect(() => {
    refreshList()
  }, [])

  const refreshList = () => {
    documentService
      .listDocuments()
      .then(setIndexedDocuments)
      .catch(() => setIndexedDocuments([]))
  }

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
        // Se era il documento attivo, deselezionalo
        if (uploadedDocument?.fileName === filename) {
          setUploadedDocument(null)
        }
        toast.success(`"${filename}" eliminato`)
        refreshList()
      })
      .catch((err) => toast.error('Errore eliminazione: ' + err.message))
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
    clearDocument,
  }
}
