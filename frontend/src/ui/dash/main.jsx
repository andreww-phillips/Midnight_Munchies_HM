import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import CustomerApp from './customer_app.jsx'
import './index.css'
import './app.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <CustomerApp />
  </StrictMode>,
)
