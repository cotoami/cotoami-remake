use anyhow::Result;
use cotoami_db::prelude::*;
use reqwest::{
    header::{HeaderMap, HeaderValue},
    Client, Response, Url,
};
use serde_json::json;
use tokio::task::spawn_blocking;

use crate::{
    api::{
        changes::Changes,
        session::{ChildSessionCreated, CreateChildSession},
        SESSION_HEADER_NAME,
    },
    csrf,
    error::{ApiError, IntoApiResult, RequestError},
    AppState,
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
    ) -> Result<ChildSessionCreated, ApiError> {
        let url = Url::parse(&self.url_prefix)?.join("/api/session/child")?;
        let req_body = CreateChildSession {
            password,
            new_password,
            child,
        };
        let response = self.client.put(url).json(&req_body).send().await?;
        if response.status() != reqwest::StatusCode::CREATED {
            return Self::into_err(response).await?;
        }
        Ok(response.json::<ChildSessionCreated>().await?)
    }

    pub async fn chunk_of_changes(&self, from: i64) -> Result<Changes, ApiError> {
        let mut url = Url::parse(&self.url_prefix)?.join("/api/changes")?;
        url.query_pairs_mut().append_pair("from", &from.to_string());
        let response = self.client.get(url).send().await?;
        if response.status() != reqwest::StatusCode::OK {
            return Self::into_err(response).await?;
        }
        Ok(response.json::<Changes>().await?)
    }

    pub async fn import_changes(
        &self,
        state: &AppState,
        parent_node: &ParentNode,
    ) -> Result<(i64, i64), ApiError> {
        let import_from = parent_node.changes_received + 1;
        let mut from = import_from;
        loop {
            // Get a chunk of changelog entries from the server
            let changes = self.chunk_of_changes(from).await?;
            let is_last_chunk = changes.is_last_chunk();
            let last_number_of_chunk = changes.last_serial_number_of_chunk();

            // Import the changes to the local database
            let state = state.clone();
            let parent_node_id = parent_node.node_id;
            let chunk_imported: Result<()> = spawn_blocking(move || {
                let db = state.db.create_session()?;
                for change in changes.chunk {
                    if let Some(imported_change) = db.import_change(&change, &parent_node_id)? {
                        state.publish_change(imported_change)?;
                    }
                }
                Ok(())
            })
            .await?;
            chunk_imported?;

            // Next chunk or finish import
            if is_last_chunk {
                return Ok((import_from, last_number_of_chunk));
            } else {
                from = last_number_of_chunk + 1;
            }
        }
    }

    async fn into_err<T>(response: Response) -> Result<T, ApiError> {
        RequestError::new("parent-node-error")
            .with_param("url", json!(response.url().to_string()))
            .with_param("status", json!(response.status().as_u16()))
            .with_param("body", json!(response.text().await?))
            .into_result()
    }
}
