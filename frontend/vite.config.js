import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    env: {
      VITE_GOOGLE_MAPS_API_KEY: '',
    },
  },
})
