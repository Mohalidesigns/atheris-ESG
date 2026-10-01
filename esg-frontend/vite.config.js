/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Backend context path is /api (esg-backend application.yml). Requests through the proxy are
      // same-origin for the browser, so the Origin header is dropped instead of whitelisting every dev port.
      // Cross-origin deployments still go through the backend CORS allow-list (CORS_ALLOWED_ORIGINS).
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
    // Absolute base so fetch Request objects work in jsdom; tests stub fetch (src/test/fakeApi.js).
    env: { VITE_API_BASE_URL: 'http://localhost/api/v1' },
    css: false,
  },
})
