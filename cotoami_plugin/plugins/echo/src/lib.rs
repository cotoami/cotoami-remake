use cotoami_plugin_api::*;
use extism_pdk::*;

const IDENTIFIER: &'static str = "app.cotoami.plugin.echo";
const NAME: &'static str = "Echo";
const COMMAND_PREFIX: &'static str = "#echo ";

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn version() -> String;
    fn post_coto(input: CotoInput) -> Coto;
}

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new(IDENTIFIER, NAME, env!("CARGO_PKG_VERSION"))
        .mark_as_agent(NAME, Vec::from(include_bytes!("icon.png"))))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    let version = unsafe { version()? };
    unsafe { log(format!("{IDENTIFIER}: node version: {version}"))? };
    unsafe { log(format!("{IDENTIFIER}: init with: {config:?}"))? };
    Ok(())
}

#[plugin_fn]
pub fn on(event: Event) -> FnResult<()> {
    match event {
        Event::CotoPosted {
            coto,
            local_node_id,
        } => {
            let content = coto.content.unwrap_or_default();
            if coto.node_id == local_node_id && content.trim().starts_with(COMMAND_PREFIX) {
                let echo = content.trim().strip_prefix(COMMAND_PREFIX).unwrap().trim();
                let input = CotoInput::new(echo, Some(coto.posted_in_id));
                unsafe { post_coto(input)? };
            }
        }
    }
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}
