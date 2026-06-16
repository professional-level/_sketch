import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      '/backtests': {
        target: process.env.VITE_BACKTEST_API_BASE_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
