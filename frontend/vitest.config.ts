import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// Unit tests for code without React or Next.js runtime (src/**/*.test.ts).
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
});
