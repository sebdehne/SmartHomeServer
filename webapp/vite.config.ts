import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vitejs.dev/config/
export default defineConfig({
    base: '/smarthome',
    plugins: [react()],
    build: {chunkSizeWarningLimit: 1600,},
    server: {
        port: 3000,
        proxy: {
            '/api': {
                target: 'http://localhost:9090',
                changeOrigin: true,
                secure: false,
                ws: true
            }
        }
    }
})
