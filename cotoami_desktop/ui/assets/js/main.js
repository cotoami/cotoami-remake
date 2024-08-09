// Execute the main method of the Scala.js application.
import 'scalajs:main.js'

// CSS imports from dependencies
// https://github.com/raquo/vite-plugin-import-side-effect/blob/master/README.md#alternatives
import 'maplibre-gl/dist/maplibre-gl.css'

// Prevent the WebView from loading a drag-and-dropped file
// https://stackoverflow.com/a/6756680
window.addEventListener("dragover", e => {
  e.preventDefault();
}, false);
window.addEventListener("drop", e => {
  e.preventDefault();
}, false);
