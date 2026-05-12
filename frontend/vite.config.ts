import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { sri } from 'vite-plugin-sri3';

export default defineConfig({
  plugins: [vue(), sri()],
  server: {
    host: true,
    port: 3001,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'happy-dom',
    globals: false,
    // Vitest matches *.spec.ts by default — exclude the Playwright e2e folder
    // so `npm test` only runs unit/component tests.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
});
