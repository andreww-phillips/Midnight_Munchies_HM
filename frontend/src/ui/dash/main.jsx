import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import RegistrationPage from './registration_page/registration_page.jsx'
import RestaurantList from './restaurant_library/restaurant_library.jsx'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* <RegistrationPage /> */}
    <RestaurantList />
  </StrictMode>,
)