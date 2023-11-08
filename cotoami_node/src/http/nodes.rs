use axum::{extract::State, middleware, routing::get, Json, Router};

use crate::{api::nodes, AppState};

pub(crate) mod children;
pub(crate) mod parents;

pub(crate) fn routes() -> Router<AppState> {
    Router::new()
        .route(
            "/local",
            get(|State(state): State<AppState>| async {
                nodes::local_node(state.db).await.map(Json)
            }),
        )
        .nest("/parents", parents::routes())
        .nest("/children", children::routes())
        .layer(middleware::from_fn(super::require_session))
}
