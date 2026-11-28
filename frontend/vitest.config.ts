import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// React나 Next.js 런타임에 의존하지 않는 순수 로직의 단위 테스트 설정입니다.
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
  },
});
