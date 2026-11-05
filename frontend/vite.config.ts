import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        // split the heavy third-party bundles out of the app chunk so
        // navigation and redeploys are cache-friendly
        manualChunks: (id) => {
          if (id.includes('node_modules')) {
            // leaf consumers only — they import from vendor, never back in
            if (id.includes('leaflet')) return 'map';
            if (id.includes('recharts') || id.includes('d3-') || id.includes('victory-vendor'))
              return 'charts';
            // everything else (react ecosystem, router, query, axios, …)
            // stays together in one chunk — splitting it further creates
            // circular cross-chunk imports
            return 'vendor';
          }
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ml': {
        target: 'http://localhost:8000',
        changeOrigin: true,
      },
    },
  },
});
