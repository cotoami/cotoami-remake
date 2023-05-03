# Cotoami UI in Scala.js

## Requirements

* [sbt](https://www.scala-sbt.org/)
* [Node.js](https://nodejs.org/en/download/releases) v18 or later
* [Yarn](https://yarnpkg.com/)

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
