import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0', // ascolta su tutte le interfacce di rete
    port: 5173,
    strictPort: true, // fallisce se la porta è occupata invece di cambiarla
  },
})
