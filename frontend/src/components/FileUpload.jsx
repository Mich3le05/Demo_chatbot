import { useRef, useState } from 'react'
import { Alert, Badge, Button, Card, Spinner } from 'react-bootstrap'
import { useDocumentUpload } from '../hooks/useDocumentUpload'

const FileUpload = ({ onDocumentProcessed }) => {
  const [dragOver, setDragOver] = useState(false)
  const fileInputRef = useRef(null)
  const {
    uploadedDocument,
    isUploading,
    uploadError,
    uploadDocument,
    clearDocument,
  } = useDocumentUpload()

  const handleFileChange = (file) => {
    if (!file) return

    const allowedExtensions = ['pdf', 'xlsx', 'xls']
    const extension = file.name.split('.').pop().toLowerCase()

    if (!allowedExtensions.includes(extension)) {
      alert('Formato non supportato. Usa PDF, XLSX o XLS.')
      return
    }

    uploadDocument(file)
  }

  const handleInputChange = (e) => {
    handleFileChange(e.target.files[0])
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragOver(false)
    handleFileChange(e.dataTransfer.files[0])
  }

  const handleDragOver = (e) => {
    e.preventDefault()
    setDragOver(true)
  }

  const handleDragLeave = () => {
    setDragOver(false)
  }

  const handleUseContext = () => {
    if (uploadedDocument) {
      onDocumentProcessed(uploadedDocument.extractedText)
    }
  }

  const handleClear = () => {
    clearDocument()
    onDocumentProcessed(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  return (
    <Card className="mb-3">
      <Card.Header>
        <h5 className="mb-0">Carica Documento</h5>
      </Card.Header>

      <Card.Body>
        {!uploadedDocument && (
          <div
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onClick={() => fileInputRef.current.click()}
            className={`border rounded p-4 text-center ${
              dragOver
                ? 'border-primary bg-primary bg-opacity-10'
                : 'border-dashed'
            }`}
            style={{ cursor: 'pointer', borderStyle: 'dashed' }}
          >
            {isUploading ? (
              <div>
                <Spinner
                  animation="border"
                  variant="primary"
                  size="sm"
                  className="me-2"
                />
                <span>Elaborazione in corso...</span>
              </div>
            ) : (
              <div>
                <p className="mb-1">
                  <strong>Trascina un file qui</strong> o clicca per
                  selezionarlo
                </p>
                <small className="text-muted">PDF, XLSX, XLS</small>
              </div>
            )}
          </div>
        )}

        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.xlsx,.xls"
          onChange={handleInputChange}
          style={{ display: 'none' }}
        />

        {uploadError && (
          <Alert
            variant="danger"
            className="mt-3 mb-0"
            dismissible
            onClose={handleClear}
          >
            {uploadError}
          </Alert>
        )}

        {uploadedDocument && (
          <div className="mt-2">
            <Alert variant="success" className="mb-2">
              <div className="d-flex justify-content-between align-items-center">
                <div>
                  <strong>✅ {uploadedDocument.fileName}</strong>
                  <div className="mt-1">
                    <Badge bg="secondary" className="me-2">
                      {uploadedDocument.fileType}
                    </Badge>
                    <Badge bg="info">
                      {uploadedDocument.fileType === 'PDF'
                        ? `${uploadedDocument.totalPages} pagine`
                        : `${uploadedDocument.totalPages} fogli`}
                    </Badge>
                  </div>
                </div>
                <Button
                  variant="outline-danger"
                  size="sm"
                  onClick={handleClear}
                >
                  Rimuovi
                </Button>
              </div>
            </Alert>

            <Button
              variant="primary"
              className="w-100"
              onClick={handleUseContext}
            >
              Carica documento nella chat
            </Button>
          </div>
        )}
      </Card.Body>
    </Card>
  )
}

export default FileUpload
