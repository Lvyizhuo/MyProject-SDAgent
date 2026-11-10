import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  // 端口与代理目标均可通过环境变量覆盖，便于与一键部署实例同时运行
  const backendPort = env.DEPLOY_BACKEND_PORT || '8080'
  return {
    plugins: [react()],
    server: {
      host: true,
      port: Number(env.DEPLOY_FRONTEND_PORT) || 5173,
      proxy: {
        '/api': {
          target: env.VITE_DEV_PROXY_TARGET || `http://localhost:${backendPort}`,
          changeOrigin: true
        }
      }
    }
  }
})
