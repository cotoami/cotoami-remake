use anyhow::Result;
use cotoami_plugin_api::*;
use extism_pdk::*;
use lazy_regex::*;

pub use self::chat_completions::*;

mod chat_completions;

const IDENTIFIER: &'static str = "app.cotoami.plugin.chatgpt";
const NAME: &'static str = "ChatGPT";

const CONFIG_API_KEY: &'static str = "api_key";
const CONFIG_MODEL: &'static str = "model";

static COMMAND_PREFIX: Lazy<Regex> = lazy_regex!(r"^\s*\#chatgpt\s");

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn ancestors_of(coto_id: String) -> Ancestors;
    fn post_coto(input: CotoInput, post_to: Option<String>) -> Coto;
    fn create_ito(input: ItoInput) -> Ito;
}

#[plugin_fn]
pub fn metadata() -> FnResult<Metadata> {
    Ok(Metadata::new(IDENTIFIER, NAME, env!("CARGO_PKG_VERSION"))
        .mark_as_agent(NAME, Vec::from(include_bytes!("icon.png"))))
}

#[plugin_fn]
pub fn init(config: Config) -> FnResult<()> {
    config.require_key(CONFIG_API_KEY)?;
    config.require_key(CONFIG_MODEL)?;
    var::set("agent_node_id", config.agent_node_id().unwrap())?;
    Ok(())
}

#[plugin_fn]
pub fn on(event: Event) -> FnResult<()> {
    match event {
        Event::CotoPosted {
            coto,
            local_node_id,
        } => {
            respond_to(coto, local_node_id)?;
        }
        Event::CotoUpdated {
            coto,
            local_node_id,
        } => {
            respond_to(coto, local_node_id)?;
        }
    }
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}

fn respond_to(coto: Coto, local_node_id: String) -> Result<()> {
    let content = coto.content.unwrap_or_default();
    if coto.node_id == local_node_id && COMMAND_PREFIX.is_match(&content) {
        let message = COMMAND_PREFIX.replace(&content, "").trim().to_owned();
        let mut messages: Vec<InputMessage> = base_messages(coto.uuid.clone())?;
        messages.push(InputMessage::by_user(message, coto.posted_by_id));
        let post_to = coto.posted_in_id.clone();
        match request_chat_completion(messages) {
            Ok(res_body) => {
                for choice in res_body.choices {
                    let coto_input = CotoInput::new(choice.message.content);
                    let reply = unsafe { post_coto(coto_input, Some(post_to.clone()))? };
                    let ito_input = ItoInput::new(coto.uuid.clone(), reply.uuid);
                    unsafe { create_ito(ito_input)? };
                }
            }
            Err(e) => {
                let input = CotoInput::new(format!("[ERROR] {e}"));
                unsafe { post_coto(input, Some(post_to))? };
            }
        }
    }
    Ok(())
}

fn base_messages(coto_id: String) -> Result<Vec<InputMessage>> {
    let agent_node_id: String = var::get("agent_node_id")?.unwrap();
    let mut ancestors = unsafe { ancestors_of(coto_id)? };
    ancestors.ancestors.reverse();
    Ok(ancestors
        .ancestors
        .into_iter()
        .map(|(_, cotos)| cotos)
        .flatten()
        .map(|coto| {
            let message = coto.content.unwrap_or_default();
            if coto.posted_by_id == agent_node_id {
                InputMessage::by_assistant(message)
            } else {
                InputMessage::by_user(message, coto.posted_by_id.clone())
            }
        })
        .collect())
}

fn request_chat_completion(messages: Vec<InputMessage>) -> Result<ResponseBody> {
    let api_key = config::get(CONFIG_API_KEY)?.unwrap();
    let req = HttpRequest::new(chat_completions::ENDPOINT)
        .with_method("POST")
        .with_header("Content-Type", "application/json")
        .with_header("Authorization", format!("Bearer {}", api_key));
    let req_body = RequestBody {
        model: config::get(CONFIG_MODEL)?.unwrap(),
        messages,
    };
    let res = http::request::<RequestBody>(&req, Some(req_body))?;
    res.json()
}
