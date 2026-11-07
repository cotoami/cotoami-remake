# Cotoami UI in Scala.js

## Requirements

* [sbt](https://www.scala-sbt.org/)
* [Node.js](https://nodejs.org/en/download/releases) 22.22.2+ (22.x), 24.15.0+ (24.x), or 26+
* [Yarn](https://yarnpkg.com/) 4.x

## Run in development mode

In development mode, use two terminals in parallel:

```shell
$ yarn
$ yarn run dev
```

```shell
sbt> ~fastLinkJS
```

## Production build

```shell
$ yarn
$ yarn run build
```

## When something goes wrong with Metals autocompile

* Restart build server
    * Open command palette (`Cmd + Shift + P`) and `Metals: Restart build server`
* Clean up
    1. Close vscode
    2. Clean up
        * `git clean -d -x -f`
        * `rm -rf ~/.bloop`
        * `ps ax | grep bloop` and `kill -KILL <bloop-pid>`
    3. Open vscode

## Scalafui sources and tests

`src/main/scala/marubinotto/{fui,router}` and their tests are vendored from
[scalafui](https://github.com/marubinotto/scalafui), commit
`799fb2900bc5637491bc312967f576ce5839009c`.
The source files are kept unchanged from upstream; application compatibility
code lives outside those packages. Feature APIs are included but the application
continues to use `Program`.

Install the JavaScript dependencies with `yarn`, then run `sbt test`.
The upstream browser/runtime tests use jsdom and run serially because they
install temporary browser globals.
