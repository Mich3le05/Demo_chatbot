import { useState } from 'react'
import { Container, Button, Alert, Spinner } from 'react-bootstrap'
import http from './utils/httpClient'

function App() {
  const [status, setStatus] = useState('idle') // idle | loading | success | error
  const [message, setMessage] = useState('')

  const testBackend = () => {
    setStatus('loading')
    setMessage('')

    // Chiamata HTTP con .then/.catch
    http
      .get('/health')
      .then((response) => {
        setStatus('success')
        setMessage(`Backend risponde: ${JSON.stringify(response)}`)
      })
      .catch((error) => {
        setStatus('error')
        setMessage(`Errore: ${error.message}`)
      })
  }

  return (
    <Container className="mt-5">
      <h1>Frontend React + Bootstrap</h1>

      <Alert variant="info" className="mt-3">
        <strong>API URL:</strong> {import.meta.env.VITE_API_URL}
      </Alert>

      <Button
        variant="primary"
        onClick={testBackend}
        disabled={status === 'loading'}
      >
        {status === 'loading' ? (
          <>
            <Spinner animation="border" size="sm" className="me-2" />
            Connessione...
          </>
        ) : (
          'Test Connessione Backend'
        )}
      </Button>

      {status === 'success' && (
        <Alert variant="success" className="mt-3">
          ✅ {message}
        </Alert>
      )}

      {status === 'error' && (
        <Alert variant="danger" className="mt-3">
          ❌ {message}
          <hr />
          <small>
            Assicurati che il backend Spring Boot sia avviato su localhost:8080
          </small>
        </Alert>
      )}
    </Container>
  )
}

export default App
