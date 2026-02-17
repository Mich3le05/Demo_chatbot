import { useState } from 'react'
import { Container, Form, Button, Card, Spinner, Alert } from 'react-bootstrap'
import { useChat } from '../hooks/useChat'

const ChatBox = ({ context }) => {
  const [inputMessage, setInputMessage] = useState('')
  const { messages, isLoading, error, sendMessage, clearChat } = useChat()
  const handleSubmit = (e) => {
    e.preventDefault()
    if (!inputMessage.trim()) return
    sendMessage(inputMessage, context) // ← Aggiungi context
    setInputMessage('')
  }

  return (
    <Container className="mt-4">
      <Card>
        <Card.Header className="d-flex justify-content-between align-items-center">
          <h5 className="mb-0">Chatbot AI</h5>
          <Button variant="outline-secondary" size="sm" onClick={clearChat}>
            Pulisci Chat
          </Button>
        </Card.Header>

        <Card.Body style={{ height: '500px', overflowY: 'auto' }}>
          {error && (
            <Alert variant="danger" dismissible>
              {error}
            </Alert>
          )}

          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`mb-3 d-flex ${
                msg.sender === 'user'
                  ? 'justify-content-end'
                  : 'justify-content-start'
              }`}
            >
              <div
                className={`p-3 rounded ${
                  msg.sender === 'user'
                    ? 'bg-primary text-white'
                    : msg.sender === 'bot'
                      ? 'bg-light'
                      : 'bg-warning'
                }`}
                style={{ maxWidth: '70%' }}
              >
                {msg.text}
              </div>
            </div>
          ))}

          {isLoading && (
            <div className="text-center">
              <Spinner animation="border" variant="primary" />
            </div>
          )}
        </Card.Body>

        <Card.Footer>
          <Form onSubmit={handleSubmit}>
            <Form.Group className="d-flex gap-2">
              <Form.Control
                type="text"
                placeholder="Scrivi un messaggio..."
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                disabled={isLoading}
              />
              <Button
                type="submit"
                disabled={isLoading || !inputMessage.trim()}
              >
                Invia
              </Button>
            </Form.Group>
          </Form>
        </Card.Footer>
      </Card>
    </Container>
  )
}

export default ChatBox
