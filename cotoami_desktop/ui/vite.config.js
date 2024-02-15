import { defineConfig } from "vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

export default defineConfig({
  publicDir: "assets/static",
  plugins: [scalaJSPlugin()],
});
