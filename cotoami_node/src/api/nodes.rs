use super::WebError;
use crate::AppState;
use axum::extract::State;
use axum::routing::get;
use axum::Router;
use cotoami_db::prelude::*;

pub(super) fn routes() -> Router<AppState> {
    Router::new().route("/local", get(local_get))
}

async fn local_get(State(_state): State<AppState>) -> Result<String, WebError> {
    Ok("local-node".into())
}

#[derive(serde::Serialize)]
struct LocalNode<'a> {
    node_id: &'a Id<Node>,
    name: &'a str,
}
