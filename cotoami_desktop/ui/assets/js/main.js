// Execute the main method of the Scala.js application.
import 'scalajs:main.js'

// Prevent the WebView from loading a drag-and-dropped file
// https://stackoverflow.com/a/6756680
window.addEventListener("dragover", e => {
  e.preventDefault();
}, false);
window.addEventListener("drop", e => {
  e.preventDefault();
}, false);
