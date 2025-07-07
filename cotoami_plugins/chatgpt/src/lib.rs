use anyhow::{bail, Result};
use cotoami_plugin_api::*;
use extism_pdk::*;
use lazy_regex::*;
use std::collections::HashMap;

pub use self::chat_completions::*;

mod chat_completions;

const IDENTIFIER: &'static str = "app.cotoami.plugin.chatgpt";
const NAME: &'static str = "ChatGPT";

const CONFIG_API_KEY: &'static str = "api_key";
const CONFIG_MODEL: &'static str = "model";
const CONFIG_DEVELOPER_MESSAGE: &'static str = "developer_message";

static COMMAND_PREFIX: Lazy<Regex> = lazy_regex!(r"^\s*\#chatgpt\s");

#[host_fn]
extern "ExtismHost" {
    fn log(message: String);
    fn ancestors_of(coto_id: String) -> Ancestors;
    fn post_coto(input: CotoInput, post_to: Option<String>) -> Coto;
    fn edit_coto(id: String, diff: CotoContentDiff) -> Coto;
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
            reply_to(coto, local_node_id)?;
        }
        Event::CotoUpdated {
            coto,
            local_node_id,
        } => {
            reply_to(coto, local_node_id)?;
        }
    }
    Ok(())
}

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}

const CONTENT_LOADING: &'static str = "![](/images/loading.svg)";

fn reply_to(coto: Coto, local_node_id: String) -> Result<()> {
    let content = coto.content.unwrap_or_default();
    if coto.node_id == local_node_id && COMMAND_PREFIX.is_match(&content) {
        // Post an empty reply with a loading icon.
        let post_to = coto.posted_in_id.clone();
        let coto_input = CotoInput::new(CONTENT_LOADING);
        let reply = unsafe { post_coto(coto_input, Some(post_to))? };
        let ito_input = ItoInput::new(coto.uuid.clone(), reply.uuid.clone());
        unsafe { create_ito(ito_input)? };

        // Base messages from the ancestor cotos.
        let (mut messages, authors) = base_messages(coto.uuid.clone())?;

        // Append the target coto as the last message.
        // Embed the user name only if it's in the authors of the base messages.
        let message = COMMAND_PREFIX.replace(&content, "").trim().to_owned();
        let author = authors.get(&coto.posted_by_id);
        messages.push(InputMessage::by_user(
            message,
            coto.posted_by_id,
            author.map(|node| node.name.clone()),
        ));

        match request_chat_completion(messages) {
            Ok(res_body) => {
                for choice in res_body.choices {
                    let mut reply_diff = CotoContentDiff::default();
                    reply_diff.content = Some(choice.message.content);
                    unsafe { edit_coto(reply.uuid.clone(), reply_diff)? };
                }
            }
            Err(e) => {
                let mut reply_diff = CotoContentDiff::default();
                reply_diff.content = Some(format!("[ERROR] {e}"));
                unsafe { edit_coto(reply.uuid, reply_diff)? };
            }
        }
    }
    Ok(())
}

fn base_messages(coto_id: String) -> Result<(Vec<InputMessage>, HashMap<String, Node>)> {
    let agent_node_id: String = var::get("agent_node_id")?.unwrap();
    let mut ancestors = unsafe { ancestors_of(coto_id)? };

    let mut messages = Vec::<InputMessage>::new();

    // If there's preceding cotos, inject a speaker tag instruction.
    if !ancestors.ancestors.is_empty() {
        messages.push(InputMessage::speaker_tag_instruction());
    }

    // Additional developer-provided instruction from config.
    if let Some(message) = config::get(CONFIG_DEVELOPER_MESSAGE)? {
        messages.push(InputMessage::by_developer(message));
    }

    // User and assistant messages
    ancestors.ancestors.reverse(); // into the order of the Ito directions
    for (_, cotos) in ancestors.ancestors.into_iter() {
        for coto in cotos {
            let message = coto.content.unwrap_or_default();
            if coto.posted_by_id == agent_node_id {
                messages.push(InputMessage::by_assistant(message));
            } else {
                let author_id = coto.posted_by_id.clone();
                let author = ancestors.authors.get(&author_id);
                messages.push(InputMessage::by_user(
                    message,
                    author_id,
                    author.map(|node| node.name.clone()),
                ));
            }
        }
    }

    Ok((messages, ancestors.authors))
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
    let status = res.status_code();
    if 200 <= status && status < 300 {
        res.json()
    } else {
        // For some reason, the response body will be empty here.
        bail!("HTTP Status {status}");
    }
}
