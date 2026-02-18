const Footer = () => {
  return (
    <div className=" p-3 mt-4 text-light">
      <h5 className="mt-2 mb-3">Sede legale - Dati fiscali</h5>
      <p>
        Organismo di vigilanza e tenuta <br /> dell'albo dei Consulenti
        Finanziari - OCF <br /> Via Tomacelli 146 00186 Roma <br /> Tel. 06
        45556100 <br /> Fax. +39 06 45556113 <br /> CF 97474000581
      </p>
      <p className="text-center">
        <span className="text-decoration-underline">
          Copyright 2009 - {new Date().getFullYear()} &copy; OCF
        </span>{' '}
        Organismo di vigilanza e tenuta dell'albo dei Consulenti Finanziari - CF
        97474000581
      </p>
    </div>
  )
}

export default Footer
