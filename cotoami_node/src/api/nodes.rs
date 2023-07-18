use crate::AppState;
use crate::WebError;
use axum::extract::State;
use axum::routing::get;
use axum::Router;

pub(in super::super) fn routes(state: AppState) -> Router<AppState> {
    Router::new()
        .route("/local", get(local_get))
        .with_state(state)
}

async fn local_get(State(_): State<AppState>) -> Result<String, WebError> {
    Ok("local-node".into())
}
