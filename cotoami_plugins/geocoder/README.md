# Cotoami Geocoder Plugin

The Geocoder plugin queries the Nominatim API using the place name written in a Coto to obtain location information, sets that data on the Coto, and makes it visible on the map.

* The Geocoder plugin interprets a line starting with `#location` as a query to the Nominatim API, using the content following the tag.
* If a Coto already has location information set, the plugin will not update it even if a `#location` tag is present.

## Download

* [cotoami_plugin_geocoder.wasm](https://github.com/cotoami/cotoami-remake/releases/latest/download/cotoami_plugin_geocoder.wasm)


## Install

* Cotoami v0.9.0 or later required.
* Copy the plugin file into the `<database-folder>/plugins` folder.
* Add the following configuration to `<database-folder>/plugins/configs.toml`.
    * If `configs.toml` does not exist, create a new file.
* Restart the application.


### Configuration

```toml
["app.cotoami.plugin.geocoder"]
allowed_hosts = "nominatim.openstreetmap.org"
allow_edit_user_content = true
```

* `allow_edit_user_content` - Allow this plugin to edit user content (required when setting a geolocation).


## Build

Debug build:

```shell
cargo build
```

Release build:

```shell
cargo build --release
```

