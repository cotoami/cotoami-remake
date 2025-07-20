use anyhow::Result;
use cotoami_plugin_api::*;
use extism_pdk::*;
use lazy_regex::*;

const IDENTIFIER: &'static str = "app.cotoami.plugin.geocoder";
const NAME: &'static str = "Geocoder";

static QUERY_PATTERN: Lazy<Regex> = lazy_regex!(r"(?m)^#geocode\s(.*)$");

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn info(message: String);
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
pub fn on(event: Event) -> FnResult<()> {
    match event {
        Event::CotoPosted {
            coto,
            local_node_id,
        } => {
            geocode(&coto, &local_node_id)?;
        }
        Event::CotoUpdated {
            coto,
            local_node_id,
        } => {
            geocode(&coto, &local_node_id)?;
        }
    }
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}

fn geocode(coto: &Coto, local_node_id: &str) -> Result<()> {
    let Some(query) = extract_query(&coto, &local_node_id) else {
        return Ok(());
    };
    unsafe { info(format!("query: {query:?}"))? };
    Ok(())
}

fn extract_query(coto: &Coto, local_node_id: &str) -> Option<String> {
    if coto.node_id != local_node_id {
        return None; // Ignore remote cotos
    }
    let Some(content) = &coto.content else {
        return None; // No content
    };
    if let Some(caps) = QUERY_PATTERN.captures(content) {
        if let Some(cap) = caps.get(1) {
            return Some(cap.as_str().trim().to_owned());
        }
    }
    None
}
