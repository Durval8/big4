import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      // More specific first: investments are served by the investments service, everything
      // else by the backend. In Docker this split is done by the nginx gateway instead.
      "/api/investments": {
        target: process.env.VITE_INVESTMENTS_PROXY_TARGET ?? "http://localhost:8081",
        changeOrigin: true,
      },
      "/api": {
        target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  preview: {
    allowedHosts: ['big4finance.online'],
  },
  server: {
    allowedHosts: ['big4finance.online'],
  },
});
