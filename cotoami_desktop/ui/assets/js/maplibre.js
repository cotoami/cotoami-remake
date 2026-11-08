import { setWorkerUrl } from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'

// MapLibre 6 ships its worker separately. Let Vite bundle it and its imports
// so the worker also resolves correctly inside Tauri's packaged WebView.
setWorkerUrl(workerUrl)
