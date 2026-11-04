import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 浏览器统一打开 http://localhost:5173，不与 127.0.0.1 混用，否则 Session Cookie 对不上
    host: 'localhost',
    port: 5173,
    strictPort: true,
    proxy: {
      // 后端 Controller 自带 /api/v1 前缀，这里只转发、不重写路径
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
