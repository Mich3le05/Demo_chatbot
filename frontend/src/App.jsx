import { Col, Container, Row } from 'react-bootstrap'
import 'bootstrap/dist/css/bootstrap.min.css'
import ChatBox from './components/ChatBox'
import logo from './assets/logo_ocf.png'
import Footer from './components/Footer'
import './assets/app.css'

function App() {
  return (
    <Container fluid className="min-vh-100 py-4 bg">
      <Row className="justify-content-center">
        <Col xs={12} md={10} lg={6}>
          <header className="bg2 rounded-3 p-3 mb-4 d-flex align-items-center justify-content-center">
            <img
              src={logo}
              alt="Logo OCF"
              style={{ height: '70px', width: 'auto' }}
              className="me-5"
            />
            <h1 className="h3 mb-0 fw-bold text-light text-center">
              Assistente AI
            </h1>
          </header>
          <main>
            <ChatBox />
          </main>
          <footer className="bg2 rounded-3">
            <Footer />
          </footer>
        </Col>
      </Row>
    </Container>
  )
}

export default App
