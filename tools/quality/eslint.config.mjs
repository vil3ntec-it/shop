/**
 * ESLint for the quality gate — correctness only. No formatting or style
 * rules, never run with --fix: the gate must not reformat any code.
 * Run from the repository root (patterns are relative to it):
 *   npx --yes eslint@9.39.5 -c tools/quality/eslint.config.mjs <files>
 */
import { ESLINT_SERVER_FILES, ES5_FILES } from './rules.mjs';

/** Node (CommonJS) globals used by the server; ES built-ins come from ecmaVersion. */
const NODE_GLOBALS = Object.fromEntries([
  'require', 'module', 'exports', '__dirname', '__filename', 'process', 'Buffer', 'console', 'global',
  'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'setImmediate', 'clearImmediate',
  'queueMicrotask', 'structuredClone', 'URL', 'URLSearchParams', 'TextEncoder', 'TextDecoder',
  'AbortController', 'AbortSignal', 'fetch', 'Headers', 'Request', 'Response', 'FormData', 'Blob', 'File',
  'crypto', 'performance', 'atob', 'btoa', 'Event', 'EventTarget', 'MessageChannel', 'DOMException',
  'ReadableStream', 'WritableStream', 'TransformStream', 'CompressionStream', 'DecompressionStream', 'WebAssembly',
].map((name) => [name, 'readonly']));

export default [
  {
    files: ESLINT_SERVER_FILES,
    languageOptions: { ecmaVersion: 2024, sourceType: 'commonjs', globals: NODE_GLOBALS },
    linterOptions: { reportUnusedDisableDirectives: 'off' },
    rules: {
      'no-undef': 'error',
      //  args: 'none' — Express error handlers must keep their 4th `next` parameter
      'no-unused-vars': ['warn', { args: 'none', caughtErrors: 'none', ignoreRestSiblings: true, varsIgnorePattern: '^_' }],
      'no-unreachable': 'error',
      'no-dupe-keys': 'error',
      'no-duplicate-case': 'error',
      'no-dupe-else-if': 'error',
      'no-const-assign': 'error',
      'no-func-assign': 'error',
      'no-import-assign': 'error',
      'no-self-assign': 'error',
      'no-self-compare': 'warn',
      'no-compare-neg-zero': 'error',
      'no-unsafe-finally': 'error',
      'no-unsafe-negation': 'error',
      'no-unsafe-optional-chaining': 'error',
      'no-setter-return': 'error',
      'no-loss-of-precision': 'error',
      'no-async-promise-executor': 'error',
      'no-shadow-restricted-names': 'error',
      'use-isnan': 'error',
      'valid-typeof': 'error',
      'no-fallthrough': 'warn',
      eqeqeq: 'off',
    },
  },
  {
    //  byte copies of the license/ UMD cores: they also publish on `window` in the browser
    files: ['server/src/lib/*-core.js'],
    languageOptions: { globals: { window: 'readonly', self: 'readonly' } },
  },
  {
    //  parse-only: a let/const/arrow/template/class here is a fatal parse error
    files: ES5_FILES,
    languageOptions: { ecmaVersion: 5, sourceType: 'script' },
    rules: {},
  },
];
