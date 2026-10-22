import nextPlugin from '@next/eslint-plugin-next';
import reactPlugin from 'eslint-plugin-react';
import hooksPlugin from 'eslint-plugin-react-hooks';
import ts from '@typescript-eslint/eslint-plugin';
import tsParser from '@typescript-eslint/parser';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';
import { existsSync, readdirSync } from 'node:fs';

/**
 * The features under src/features, one per backend context (docs/architecture.md). Read from the
 * folder so that a new feature gets its boundary without touching this file.
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
      'next-env.d.ts', // next build가 다시 만든다
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
        version: 'detect', // You can add this if you get a warning about the React version when you lint.
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
      // Allow underscore-prefixed identifiers to be intentionally unused.
      // Standard convention for parameters/variables whose position is required
      // but whose value is deliberately ignored (e.g., `(_, index) => ...`).
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
  // Boundaries (docs/architecture.md): shared knows no feature; a feature knows no other feature.
  // What spans features is put together in app/, as apps/api's composition does on the backend.
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
  eslintPluginPrettierRecommended, // eslint-config-prettier 기능을 포함한다.
  {
    files: ['**/*.d.ts'],
    rules: {
      '@typescript-eslint/no-unused-vars': 'off',
    },
  },
];
