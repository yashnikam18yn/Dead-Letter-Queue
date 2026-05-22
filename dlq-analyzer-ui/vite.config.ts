import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define:{
    global: 'window',
  },
  server: {
    port: 3000,
    proxy:{
      '/api':{
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/ws':{
        target: 'http://localhost:8081',
        changeOrigin: true,
        ws: true
      }
    }
  }
})
