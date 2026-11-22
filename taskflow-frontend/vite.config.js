import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

function productionApiUrlProblem(value) {
  if (!value || !value.trim()) {
    return 'VITE_API_BASE_URL is required for production builds.';
  }
  let url;
  try {
    url = new URL(value.trim());
  } catch {
    return 'VITE_API_BASE_URL must be an absolute http(s) URL (for example https://api.example.com/api/v1).';
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    return 'VITE_API_BASE_URL must use http or https.';
  }
  if (!url.hostname) {
    return 'VITE_API_BASE_URL must include a host.';
  }
  return null;
}

export default defineConfig(({ command, mode }) => {
  if (command === 'build' && mode === 'production') {
    const env = loadEnv(mode, process.cwd(), 'VITE_');
    const problem = productionApiUrlProblem(env.VITE_API_BASE_URL);
    if (problem) {
      throw new Error(`[TaskFlow] ${problem}`);
    }
  }

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'react-vendor': ['react', 'react-dom', 'react-router-dom'],
            'motion-vendor': ['framer-motion'],
            'icons-vendor': ['lucide-react'],
          },
        },
      },
    },
  };
});
