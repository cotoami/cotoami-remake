use cotoami_plugin_api::*;
use extism_pdk::*;

const IDENTIFIER: &'static str = "app.cotoami.plugin.geocoder";
const NAME: &'static str = "Geocoder";

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn edit_coto(id: String, diff: CotoContentDiff) -> Coto;
}

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new(IDENTIFIER, NAME, env!("CARGO_PKG_VERSION"))
        .mark_as_agent(NAME, Vec::from(include_bytes!("icon.png"))))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    unsafe { log(format!("{IDENTIFIER}: init with: {config:?}"))? };
    Ok(())
}

#[plugin_fn]
pub fn on(_event: Event) -> FnResult<()> {
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}
