# Work log

## Generate an initial app

### 1. Create initial UI assets

```shell
$ mkdir ui
```

Create an HTML file `ui/index.html` as below:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Cotoami</title>
  </head>
  <body>
    <h1>Let's develop Cotoami Remake!</h1>
  </body>
</html>
```

### 2. Install Tauri CLI

```shell
$ cargo install tauri-cli

$ cargo tauri --version
tauri-cli 1.2.3
```

### 3. Scaffold a minimal Rust project

```shell
$ cargo tauri init
? What is your app name? › Cotoami
? What should the window title be? › Cotoami
? Where are your web assets (HTML/CSS/JS) located ... › ../ui
? What is the url of your dev server? › ../ui
? What is your frontend dev command? ›
? What is your frontend build command? ›
```

### 4. Change the bundle identifier

> You must change the bundle identifier in `tauri.conf.json > tauri > bundle > identifier`. The default value `com.tauri.dev` is not allowed as it must be unique across applications.

* Change the identifier to `me.cotoa.dev`

### 5. Rename the Rust project directory

* Rename `src-tauri` to `tauri`
* [Tauri forced layout of \`src\-tauri\` prevents some organizational strategies · Issue \#2643 · tauri\-apps/tauri](https://github.com/tauri-apps/tauri/issues/2643)

### 6. Test development and production build

```shell
$ cargo tauri dev
```

```shell
$ cargo tauri build
```
