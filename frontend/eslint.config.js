import nextPlugin from '@next/eslint-plugin-next';
import reactPlugin from 'eslint-plugin-react';
import hooksPlugin from 'eslint-plugin-react-hooks';
import ts from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';
import { existsSync, readdirSync } from 'node:fs';

/**
 * 백엔드 컨텍스트와 1:1로 대응하는 `src/features` 하위 기능 목록입니다(docs/architecture/01-module-structure.md).
 * 새 기능을 추가할 때 이 설정 파일을 수정하지 않아도 모듈 경계가 자동 적용되도록 디렉터리에서 동적으로 읽어옵니다.
 */
const FEATURES = existsSync('src/features')
  ? readdirSync('src/features', { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
  : [];

const TO_APP = {
  group: ['@/app', '@/app/**'],
  message: '페이지(app)는 기능과 공용 코드를 쓰는 쪽이다. 거꾸로 쓰지 않는다.',
};

export default [
  {
    ignores: [
      'tailwind.config.ts',
      'next.config.ts',
      'vitest.config.ts',
      'next-env.d.ts', // next build 시 자동 생성되는 파일입니다.
      'node_modules/**',
      '.next/**',
      'out/**',
      'public/**',
    ],
  },
  {
    plugins: {
      react: reactPlugin,
    },
    rules: {
      ...reactPlugin.configs['jsx-runtime'].rules,
    },
    settings: {
      react: {
        version: 'detect',
      },
    },
  },
  {
    plugins: {
      'react-hooks': hooksPlugin,
    },
    rules: hooksPlugin.configs.recommended.rules,
  },
  {
    plugins: {
      '@next/next': nextPlugin,
    },
    rules: {
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs['core-web-vitals'].rules,
    },
  },
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaFeatures: { modules: true },
        ecmaVersion: 'latest',
        project: './tsconfig.json',
      },
    },
    plugins: {
      '@typescript-eslint': ts,
    },
    rules: {
      ...ts.configs['eslint-recommended'].rules,
      ...ts.configs['recommended'].rules,
      // 위치상 선언이 필요하지만 사용하지 않는 변수나 매개변수는 `_` 접두사로 허용합니다.
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          destructuredArrayIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
    },
  },
  // 모듈 경계 규칙입니다(docs/architecture/01-module-structure.md).
  // shared는 개별 기능을 참조할 수 없고, 각 기능은 다른 기능을 직접 참조할 수 없습니다.
  // 여러 기능을 조합하는 역할은 백엔드의 apps/api와 마찬가지로 app/ 계층에서 담당합니다.
  {
    files: ['src/shared/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/features', '@/features/**'],
              message: '공용 코드(shared)는 기능을 모른다.',
            },
            TO_APP,
          ],
        },
      ],
    },
  },
  ...FEATURES.map((feature) => ({
    files: [`src/features/${feature}/**/*.{ts,tsx}`],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: [
                '@/features/*',
                '@/features/*/**',
                `!@/features/${feature}`,
                `!@/features/${feature}/**`,
              ],
              message:
                '다른 기능의 코드를 쓰지 않는다. 여러 기능을 모으는 일은 app/의 페이지가 한다.',
            },
            TO_APP,
          ],
        },
      ],
    },
  })),
  eslintPluginPrettierRecommended, // eslint-config-prettier 설정을 함께 적용합니다.
  {
    files: ['**/*.d.ts'],
    rules: {
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
];
