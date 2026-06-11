import { defineConfig } from "vite";

// The dev server proxies the Java edge (TraderDesktopEdgeMain: REST :8484, WS :8485)
// so the browser sees one origin — no CORS on the edge, same as production where the
// desktop is served behind the same gateway.
export default defineConfig({
  optimizeDeps: {
    // Perspective ships prebundled ESM + WASM; esbuild pre-bundling corrupts the
    // worker/wasm asset graph, so it is excluded from dependency optimization.
    exclude: [
      "@finos/perspective",
      "@finos/perspective-viewer",
      "@finos/perspective-viewer-datagrid",
    ],
  },
  server: {
    proxy: {
      "/api": "http://localhost:8484",
      "/ws": {
        target: "ws://localhost:8485",
        ws: true,
      },
    },
  },
  build: {
    target: "es2022",
  },
});
