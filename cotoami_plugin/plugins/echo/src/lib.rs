use cotoami_plugin_api::*;
use extism_pdk::*;

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new("app.cotoami.plugin.echo", "Echo", "0.1.0")
        .mark_as_agent("Echo", Vec::from(include_bytes!("icon.png"))))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    info!("init: {config:?}");
    Ok(())
}
