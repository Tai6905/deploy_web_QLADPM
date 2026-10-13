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
        bypass: (request) =>
          request.headers.accept?.includes("text/html")
            ? "/index.html"
            : undefined,
      },
      "/categories": {
        target: "http://localhost:18081",
        changeOrigin: true,
      },
    },
  },
});
