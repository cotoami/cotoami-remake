use anyhow::Result;
use cotoami_db::prelude::Node;
use reqwest::{Client, Response, Url};

use crate::api::session::CreateChildSession;

pub(crate) struct Server {
    client: Client,
    url_prefix: String,
}

impl Server {
    pub fn new(url_prefix: String) -> Self {
        Self {
            client: Client::new(),
            url_prefix,
        }
    }

    pub fn url_prefix(&self) -> &str { &self.url_prefix }

    pub async fn create_child_session(
        &self,
        password: String,
        new_password: Option<String>,
        child: Node,
    ) -> Result<Response> {
        let url = Url::parse(&self.url_prefix)?.join("/api/session/child")?;
        let req_body = CreateChildSession {
            password,
            new_password,
            child,
        };
        let response = self.client.put(url).json(&req_body).send().await?;
        Ok(response)
    }
}
