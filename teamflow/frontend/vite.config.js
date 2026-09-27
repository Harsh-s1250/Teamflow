import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// No hard-coded backend URL here: the frontend always talks to
// VITE_API_BASE_URL (see .env.example), so the same build artifact works
// unchanged across local/test/production - only the environment variable
// that produced the build differs.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
})
