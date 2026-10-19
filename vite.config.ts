import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", ["VITE_", "BACKEND_"]);
  const backendProxy = {
    target: env.BACKEND_PROXY_URL || "http://127.0.0.1:18081",
    changeOrigin: true,
  };
  const spaAwareProxy = {
    ...backendProxy,
    bypass: (request: { headers: { accept?: string } }) =>
      request.headers.accept?.includes("text/html") ? "/index.html" : undefined,
  };
  const proxy = {
    "/api": backendProxy,
    "/products": spaAwareProxy,
    "/categories": backendProxy,
    "/dashboard": backendProxy,
    "/orders": spaAwareProxy,
    "/inventory": spaAwareProxy,
    "/receivings": backendProxy,
    "/deliveries": backendProxy,
    "/returns": spaAwareProxy,
  };
  return {
    plugins: [vue()],
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, strictPort: true, proxy },
  };
});
