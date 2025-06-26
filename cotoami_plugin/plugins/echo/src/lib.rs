use cotoami_plugin_api::*;
use extism_pdk::*;

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata {
        identifier: "app.cotoami.plugin.echo".into(),
        name: "Echo".into(),
        version: "0.1.0".into(),
    })
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    info!("init: {config:?}");
    Ok(())
}
