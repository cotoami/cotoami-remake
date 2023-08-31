use anyhow::Result;
use cotoami_db::prelude::Node;
use reqwest::{
    header::{HeaderMap, HeaderValue},
    Client, Response, Url,
};

use crate::{
    api::{session::CreateChildSession, SESSION_HEADER_NAME},
    csrf,
};

pub(crate) struct Server {
    client: Client,
    url_prefix: String,
}

impl Server {
    pub fn new(url_prefix: String, session_token: Option<&str>) -> Result<Self> {
        // Default request headers
        let mut headers = HeaderMap::new();
        headers.insert(
            csrf::CUSTOM_HEADER,
            HeaderValue::from_static("cotoami_node"),
        );
        if let Some(token) = session_token {
            let mut token = HeaderValue::from_str(token)?;
            token.set_sensitive(true);
            headers.insert(SESSION_HEADER_NAME, token);
        };

        let client = Client::builder().default_headers(headers).build()?;
        Ok(Self { client, url_prefix })
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
