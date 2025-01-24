import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

export default defineConfig({
  publicDir: "assets/static",
  plugins: [scalaJSPlugin()],
  server: {
    watch: {
      // Testing this option because watch stopped working on macOS.
      usePolling: true
    }
  }
});
