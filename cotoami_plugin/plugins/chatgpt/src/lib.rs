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
    fn post_coto(input: CotoInput) -> Coto;
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
            if coto.node_id == local_node_id && COMMAND_PREFIX.is_match(&content) {
                let message = COMMAND_PREFIX.replace(&content, "").trim().to_owned();
                let messages = vec![InputMessage {
                    role: "user".into(),
                    content: message,
                    name: None,
                }];
                match send_request(messages) {
                    Ok(res_body) => {
                        for choice in res_body.choices {
                            let input = CotoInput::new(
                                choice.message.content,
                                Some(coto.posted_in_id.clone()),
                            );
                            unsafe { post_coto(input)? };
                        }
                    }
                    Err(e) => {
                        let input =
                            CotoInput::new(format!("[ERROR] {e}"), Some(coto.posted_in_id.clone()));
                        unsafe { post_coto(input)? };
                    }
                }
            }
        }
    }
    Ok(())
}

fn send_request(messages: Vec<InputMessage>) -> Result<ResponseBody> {
    let api_key = config::get(CONFIG_API_KEY)?.unwrap();
    let req = HttpRequest::new("https://api.openai.com/v1/chat/completions")
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

#[plugin_fn]
pub fn destroy() -> FnResult<()> {
    Ok(())
}
