import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
// @ts-expect-error path is available in node environment
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
})
