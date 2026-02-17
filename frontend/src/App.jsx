import { useState } from 'react'
import { Col, Container, Row, Badge, Card } from 'react-bootstrap'
import 'bootstrap/dist/css/bootstrap.min.css'
import ChatBox from './components/ChatBox'
import FileUpload from './components/FileUpload'
import logo from './assets/logo_ocf.png'

function App() {
  const [context, setContext] = useState(null)

  const handleDocumentProcessed = (extractedText) => {
    setContext(extractedText)
  }

  return (
    <div
      style={{
        backgroundColor: '#f0f2f5',
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* HEADER: Isolato e perfettamente centrato */}
      <header className="py-4 w-100">
        <Container>
          <Row className="justify-content-center">
            <Col xs="auto" className="d-flex align-items-center">
              <img
                src={logo}
                alt="Logo OCF"
                style={{ height: '60px', width: 'auto' }}
                className="me-3"
              />
              <div className="border-start ps-3 border-2">
                <h1 className="h3 mb-0 fw-bold text-dark">
                  Organismo Consulenti Finanziari
                </h1>
                <h4 className="my-1 text-muted text-center">Assistente AI</h4>
              </div>
            </Col>
          </Row>
        </Container>
      </header>

      <main className="flex-grow-1 pb-5">
        <Container style={{ maxWidth: '1100px' }}>
          <Row className="g-4">
            <Col lg={4}>
              <Card className="shadow-sm border-0 rounded-3 h-100">
                <Card.Body className="p-4 d-flex flex-column">
                  <div className="flex-grow-1">
                    <FileUpload onDocumentProcessed={handleDocumentProcessed} />
                  </div>

                  <div
                    className={`mt-4 p-3 rounded-3 text-center ${context ? 'bg-light-success' : 'bg-light'}`}
                    style={{ border: '1px solid #e0e0e0' }}
                  >
                    {context ? (
                      <Badge bg="success">Documento Caricato</Badge>
                    ) : (
                      <>
                        <div className="small text-muted italic">
                          Carica un file PDF/Excel
                        </div>
                      </>
                    )}
                  </div>
                </Card.Body>
              </Card>
            </Col>

            <Col lg={8}>
              <Card
                className="shadow-sm border-0 rounded-3 overflow-hidden"
                style={{ height: '700px' }}
              >
                <Card.Body className="p-0 h-100">
                  <ChatBox context={context} />
                </Card.Body>
              </Card>
            </Col>
          </Row>
        </Container>
      </main>
    </div>
  )
}

export default App
