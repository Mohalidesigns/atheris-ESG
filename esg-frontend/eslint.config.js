import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import i18next from 'eslint-plugin-i18next'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist', 'cypress/screenshots', 'cypress/videos', 'cypress/logs']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
      parserOptions: {
        ecmaVersion: 'latest',
        ecmaFeatures: { jsx: true },
        sourceType: 'module',
      },
    },
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: '^[A-Z_]' }],
    },
  },
  {
    // WP-45 (D45): no hard-coded text in the interface — wrap it in tr() (npm run i18n:wrap does it) so it can be translated.
    files: ['src/**/*.jsx'],
    ignores: ['src/**/*.test.jsx', 'src/test/**'],
    plugins: { i18next },
    rules: {
      'i18next/no-literal-string': ['error', {
        mode: 'jsx-text-only',
        // Code samples, header names and error codes stay as they are; so do numbers and symbols.
        'jsx-components': { exclude: ['code', 'pre', 'kbd', 'Trans'] },
        words: { exclude: ['^[\\d\\s/·%.,:;+()×=—–\\-~−→“”‘’✓…_]*$', '^ESG-[0-9A-Z-]+$'] },
      }],
    },
  },
  {
    // Build tooling runs in Node, not the browser
    files: ['*.config.js', 'scripts/**/*.mjs'],
    languageOptions: { globals: globals.node },
  },
  {
    // Cypress E2E (WP-18): mocha + Cypress globals
    files: ['cypress/**/*.js'],
    languageOptions: { globals: { ...globals.browser, ...globals.mocha, cy: 'readonly', Cypress: 'readonly', expect: 'readonly' } },
  },
])
