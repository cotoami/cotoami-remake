use cotoami_plugin_api::*;
use extism_pdk::*;

#[plugin_fn]
pub fn metadata() -> FnResult<PluginMetadata> {
    Ok(PluginMetadata {
        identifier: "app.cotoami.plugin.echo".into(),
        name: "Echo".into(),
        version: "0.1.0".into(),
    })
}
