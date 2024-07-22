# Notes on Tauri

## Dropping files on WebView

* `fileDropEnabled` in `tauri.config.json` needs to be `false` to use the browser's drag n' drop API.
    * <https://github.com/tauri-apps/tauri/issues/2768#issuecomment-2067371535>
    * [ElectronからTauriに移行しようとしてD&Dで詰んだ話 \#Electron \- Qiita](https://qiita.com/mrin/items/efe899943c3f69d53353)
