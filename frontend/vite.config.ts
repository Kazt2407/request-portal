import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    strictPort: true,
    // The API is served from the same origin, as in production, so no CORS is needed for it.
    proxy: { '/api': process.env.API_URL ?? 'http://localhost:8080' },
  },
})
