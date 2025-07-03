use core::time::Duration;

use anyhow::Result;
use axum::{
    extract::State,
    http::StatusCode,
    middleware,
    routing::{delete, put},
    Extension, Form, Json, Router,
};
use axum_extra::{
    extract::cookie::{Cookie, CookieJar, Expiration, SameSite},
    TypedHeader,
};
use cotoami_db::prelude::*;
use time::OffsetDateTime;
use tokio::task::spawn_blocking;
use tracing::info;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{ClientNodeSession, CreateClientNodeSession, SessionToken},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", delete(delete_session))
        .route_layer(middleware::from_fn(crate::web::require_session))
        .route("/owner", put(create_owner_session))
        .route("/client-node", put(create_client_node_session))
}

fn create_cookie<'a>(token: &SessionToken) -> Cookie<'a> {
    let expiration = Expiration::DateTime(
        OffsetDateTime::from_unix_timestamp_nanos(
            token
                .expires_at
                .and_utc()
                .timestamp_nanos_opt()
                .unwrap_or_else(|| unreachable!()) as i128,
        )
        .unwrap_or_else(|_| unreachable!()),
    );
    Cookie::build((super::SESSION_COOKIE_NAME, token.token.clone()))
        .secure(true)
        .http_only(true)
        .path("/")
        .same_site(SameSite::Lax)
        .expires(expiration)
        .into()
}

/////////////////////////////////////////////////////////////////////////////
// DELETE /api/session
/////////////////////////////////////////////////////////////////////////////

async fn delete_session(
    State(state): State<NodeState>,
    Extension(client_session): Extension<ClientSession>,
    jar: CookieJar,
) -> Result<CookieJar, ServiceError> {
    spawn_blocking(move || {
        let db = state.db().new_session()?;
        match &client_session {
            ClientSession::Operator(Operator::LocalNode(_)) => {
                db.clear_owner_session()?;
            }
            ClientSession::Operator(Operator::ChildNode(child)) => {
                db.clear_client_node_session(&child.node_id)?;
            }
            ClientSession::Operator(Operator::Agent(_)) => {
                // No session data to be deleted
            }
            ClientSession::Operator(Operator::Anonymous) => {
                // No session data to be deleted
            }
            ClientSession::ParentNode(parent) => {
                db.clear_client_node_session(&parent.node_id)?;
            }
        }
        info!("Deleted a client session: {:?}", client_session);
        Ok(jar.remove(Cookie::from(super::SESSION_COOKIE_NAME)))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/session/owner
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct CreateOwnerSession {
    #[validate(required)]
    password: Option<String>,
}

async fn create_owner_session(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    jar: CookieJar,
    Form(form): Form<CreateOwnerSession>,
) -> Result<(StatusCode, CookieJar, Content<SessionToken>), ServiceError> {
    if let Err(errors) = form.validate() {
        return errors.into_result();
    }
    spawn_blocking(move || {
        let db = state.db().new_session()?;
        let local_node = db.start_owner_session(
            &form.password.unwrap(), // validated to be Some
            Duration::from_secs(state.read_config().session_seconds()),
        )?;
        let token = SessionToken {
            token: local_node.owner_session_token.unwrap(),
            expires_at: local_node.owner_session_expires_at.unwrap(),
        };
        let cookie = create_cookie(&token);
        Ok((StatusCode::CREATED, jar.add(cookie), Content(token, accept)))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/session/client-node
/////////////////////////////////////////////////////////////////////////////

async fn create_client_node_session(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    mut jar: CookieJar,
    Json(payload): Json<CreateClientNodeSession>,
) -> Result<(StatusCode, CookieJar, Content<ClientNodeSession>), ServiceError> {
    let session = state.create_client_node_session(payload).await?;
    if let Some(ref token) = session.token {
        let cookie = create_cookie(token);
        jar = jar.add(cookie);
    }
    Ok((StatusCode::CREATED, jar, Content(session, accept)))
}
