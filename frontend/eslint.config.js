import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
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

      // eslint-plugin-react-hooks v7 新增的 React Compiler 静态分析规则。
      // 当前代码库尚未引入 React Compiler，且这些规则存在已知误报
      // （例如 Date.now() 出现在事件处理函数体内也会被判定为 render 期不纯）。
      // 先降级为 warning 作为技术债跟踪，待引入 Compiler 时再逐个收敛为 error。
      'react-hooks/immutability': 'warn',
      'react-hooks/purity': 'warn',
      'react-hooks/set-state-in-effect': 'warn',
    },
  },
])
