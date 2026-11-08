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

`src/main/scala/marubinotto/{fui,router,react}` and their tests are vendored from
[scalafui](https://github.com/marubinotto/scalafui), commit
`3fb578bb967f32e16ea63e35f06eac8f592cb9a1`.
These packages must be synchronized together, including their tests.
The source files are kept unchanged from upstream; application compatibility
code lives outside those packages. Feature APIs are included but the application
continues to use `Program`.

Install the JavaScript dependencies with `yarn`, then run `sbt test`.
The upstream browser/runtime tests use jsdom and run serially because they
install temporary browser globals.


The UI uses `marubinotto.react` with React / React DOM 19.3.1. External component
props derive `PropsEncoder`; omitted optional props and explicit null are
separate values. See the Scalafui README for the facade API and interop rules.
Development changes reload the page and reset application state; Fast Refresh
is not part of this migration.
