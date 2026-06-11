import { defineConfig } from "vite";

// Both servers proxy the Java edge (TraderDesktopEdgeMain: REST :8484, WS :8485) so the
// browser sees one origin — no CORS on the edge, same as production where the desktop is
// served behind the same gateway. The demo launcher serves the production build via
// `vite preview` (Perspective's WASM worker bootstraps reliably from built assets; the
// dev server's module graph stalls it — see docs/TRADER_DESKTOP_DEMO.md troubleshooting).
const edgeProxy = {
  "/api": "http://localhost:8484",
  "/ws": {
    target: "ws://localhost:8485",
    ws: true,
  },
} as const;

export default defineConfig({
  optimizeDeps: {
    // Perspective ships prebundled ESM + WASM; esbuild pre-bundling corrupts the
    // worker/wasm asset graph, so it is excluded from dependency optimization.
    exclude: [
      "@finos/perspective",
      "@finos/perspective-viewer",
      "@finos/perspective-viewer-datagrid",
    ],
    // CJS dependencies of the excluded packages still need esbuild's CJS->ESM
    // interop in dev mode (Rollup handles them in the production build).
    include: ["chroma-js"],
  },
  server: {
    proxy: edgeProxy,
  },
  preview: {
    proxy: edgeProxy,
  },
  build: {
    target: "es2022",
  },
});
