import { useState, useRef, useEffect } from 'react'
import { Form, Button, Card, Spinner, Badge } from 'react-bootstrap'
import { useChat } from '../hooks/useChat'
import { useDocumentUpload } from '../hooks/useDocumentUpload'
import Error from './Error'
import Loading from './Loading'
import ReactMarkdown from 'react-markdown'

const ChatBox = () => {
  const [inputMessage, setInputMessage] = useState('')
  const scrollRef = useRef(null)
  const fileInputRef = useRef(null)

  const { messages, isLoading, error, sendMessage, clearChat } = useChat()
  const { uploadedDocument, isUploading, uploadDocument, clearDocument } =
    useDocumentUpload()

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages])

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!inputMessage.trim()) return

    const useRag = !!uploadedDocument
    const sourceFile = uploadedDocument?.fileName || null

    sendMessage(inputMessage, null, useRag, sourceFile)
    setInputMessage('')
  }

  return (
    <Card className="bg2 text-light shadow-lg border-0 rounded-3 d-flex flex-column">
      <Card.Header className="d-flex align-items-center justify-content-between py-3">
        <span className="fw-bold fs-4">Chat OCF</span>
        <div>
          <Button
            variant="outline-light"
            className="bg3 border-0 shadow-sm me-3 px-3 py-2 rounded-3"
            size="sm"
            onClick={() => fileInputRef.current.click()}
            disabled={isUploading}
          >
            {isUploading ? (
              <Spinner animation="border" size="sm" />
            ) : (
              'Carica documento'
            )}
          </Button>
          <Button
            variant="outline-light"
            className="bg3 border-0 shadow-sm px-3 py-2 rounded-3"
            size="sm"
            onClick={clearChat}
          >
            Pulisci chat
          </Button>
        </div>
      </Card.Header>

      <input
        type="file"
        ref={fileInputRef}
        style={{ display: 'none' }}
        onChange={(e) => uploadDocument(e.target.files[0])}
      />

      <Card.Body
        ref={scrollRef}
        className="p-4 flex-grow-1 overflow-auto"
        style={{ height: '600px' }}
      >
        {error && <Error message={error} />}

        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`mb-3 d-flex ${msg.sender === 'user' ? 'justify-content-end' : 'justify-content-start'}`}
          >
            <div
              className={`p-3 ${
                msg.sender === 'user'
                  ? 'bg-success text-white fw-semibold rounded-start rounded-top'
                  : 'bg-light fw-semibold text-dark rounded-end rounded-top'
              }`}
              style={{
                maxWidth: '85%',
                borderRadius: '18px',
                boxShadow: '0 2px 5px rgba(0,0,0,0.2)',
              }}
            >
              <div className="small fw-bold mb-1" style={{ opacity: 0.7 }}>
                {msg.sender === 'user' ? 'Tu' : 'Assistente'}
              </div>
              <ReactMarkdown>{msg.text}</ReactMarkdown>
            </div>
          </div>
        ))}

        {isLoading && <Loading />}
      </Card.Body>

      <Card.Footer className="p-3 border-0 bg2">
        {uploadedDocument && (
          <div className="mb-2 d-flex align-items-center animate-fade-in">
            <Badge bg="success" className="p-2 d-flex align-items-center gap-2">
              <span>{uploadedDocument.fileName}</span>
              <span
                style={{ cursor: 'pointer', lineHeight: 1, fontSize: '1.1rem' }}
                onClick={clearDocument}
                className="fw-bold"
              >
                &times;
              </span>
            </Badge>
            <medium className="ms-2 text-success opacity-75">
              Documento caricato
            </medium>
          </div>
        )}

        <Form onSubmit={handleSubmit}>
          <Form.Group className="d-flex gap-2">
            <Form.Control
              type="text"
              className="text-light bg3 border-0 rounded-4 chat-input shadow-md px-3"
              placeholder="Fai una domanda"
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              disabled={isLoading}
            />
            <Button
              type="submit"
              variant="outline-success"
              className="bg3 border-0 py-2 px-3 rounded-4 shadow-md"
              style={{
                cursor:
                  isLoading || (!inputMessage.trim() && !uploadedDocument)
                    ? 'not-allowed'
                    : 'pointer',
              }}
              aria-disabled={
                isLoading || (!inputMessage.trim() && !uploadedDocument)
              }
            >
              Invia
            </Button>
          </Form.Group>
        </Form>
      </Card.Footer>
    </Card>
  )
}

export default ChatBox
