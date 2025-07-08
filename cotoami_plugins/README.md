# Cotoami Plugins

The Cotoami Plugin is a WebAssembly module that extends the functionality of a Cotoami Node. 

* The plugin system was introduced in version [v0.8.0](https://github.com/cotoami/cotoami-remake/releases/tag/desktop-v0.8.0).
* To install a plugin, download the module file and copy it into the `<database-folder>/plugins` directory.
* Plugins are developed using the [Plugin API](/cotoami_node/crates/plugin_api).


## Available Plugins

* [Echo Plugin](echo)
    * A simple plugin that mimics and reposts the content of any Coto that starts with `#echo`.
* [ChatGPT Plugin](chatgpt)
    * The ChatGPT agent responds to any Coto that begins with `#chatgpt`. By leveraging Cotoami’s features, it can understand the surrounding knowledge graph as context.
