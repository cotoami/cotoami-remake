use cotoami_plugin_api::*;
use extism_pdk::*;

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(
        Metadata::new("app.cotoami.plugin.echo", "Echo", env!("CARGO_PKG_VERSION"))
            .mark_as_agent("Echo", Vec::from(include_bytes!("icon.png"))),
    )
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    info!("init: {config:?}");
    Ok(())
}

#[plugin_fn]
pub fn on(event: Event) -> FnResult<()> {
    info!("on: {event:?}");
    Ok(())
}
