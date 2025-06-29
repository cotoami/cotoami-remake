use cotoami_plugin_api::*;
use extism_pdk::*;

const IDENTIFIER: &'static str = "app.cotoami.plugin.echo";
const NAME: &'static str = "Echo";

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn version() -> String;
}

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new(IDENTIFIER, NAME, env!("CARGO_PKG_VERSION"))
        .mark_as_agent(NAME, Vec::from(include_bytes!("icon.png"))))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    let version = unsafe { version()? };
    unsafe {
        log(format!(
            "{IDENTIFIER}: init called from {version}: {config:?}"
        ))?
    };
    Ok(())
}

#[plugin_fn]
pub fn on(event: Event) -> FnResult<()> {
    unsafe { log(format!("{IDENTIFIER}: {event:?}"))? };
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}
