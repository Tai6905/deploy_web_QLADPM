import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:18081",
        changeOrigin: true,
      },
      "/products": {
        target: "http://localhost:18081",
        changeOrigin: true,
      },
      "/categories": {
        target: "http://localhost:18081",
        changeOrigin: true,
      },
    },
  },
});
