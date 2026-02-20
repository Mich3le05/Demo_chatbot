import { Spinner } from 'react-bootstrap'

const Loading = () => (
  /*<div className=" p-5 text-center d-flex align-items-center justify-content-center">
    <Spinner animation="border" role="status" />
    <span className="ms-2">Caricamento in corso...</span>
  </div>*/
  <div className="text-start mb-3">
    <Spinner animation="grow" variant="light" size="sm" />
  </div>
)

export default Loading
