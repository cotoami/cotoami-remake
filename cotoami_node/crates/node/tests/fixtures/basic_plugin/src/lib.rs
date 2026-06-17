use cotoami_plugin_api::*;
use extism_pdk::*;

const IDENTIFIER: &str = "app.cotoami.test.basic-plugin";
const NAME: &str = "Basic Test Plugin";
const COMMAND_PREFIX: &str = "#plugin-test ";

#[host_fn]
extern "ExtismHost" {
    fn info(message: String);
    fn post_coto(input: CotoInput, post_to: Option<String>) -> Coto;
}

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new(IDENTIFIER, NAME, env!("CARGO_PKG_VERSION"))
        .mark_as_agent(NAME, Vec::from([1_u8, 2, 3, 4])))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    for _ in 0..1_000_000 {
        core::hint::spin_loop();
    }
    let agent_node_id = config.agent_node_id().unwrap_or("<missing>");
    unsafe { info(format!("{IDENTIFIER}: initialized with agent {agent_node_id}"))? };
    Ok(())
}

#[plugin_fn]
pub fn on(event: Event) -> FnResult<()> {
    let Event::CotoPosted {
        coto,
        local_node_id,
    } = event else {
        return Ok(());
    };

    if coto.node_id != local_node_id {
        return Ok(());
    }

    let Some(content) = coto.content else {
        return Ok(());
    };

    let Some(message) = content.strip_prefix(COMMAND_PREFIX) else {
        return Ok(());
    };

    unsafe { info(format!("{IDENTIFIER}: handling command"))? };
    let input = CotoInput::new(format!("plugin reply: {}", message.trim()));
    unsafe { post_coto(input, Some(coto.posted_in_id))? };
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    let result = Ok(());
    result
}
