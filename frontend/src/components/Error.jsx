import { Alert } from 'react-bootstrap'

const Error = ({ message }) => (
  <Alert variant="danger" className=" p-5">
    {message}
  </Alert>
)

export default Error
