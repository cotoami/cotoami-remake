use core::time::Duration;

use anyhow::Result;
use axum::{extract::State, http::StatusCode, Extension, Form, Json};
use axum_extra::extract::cookie::{Cookie, CookieJar, Expiration, SameSite};
use cotoami_db::prelude::*;
use time::OffsetDateTime;
use tokio::task::spawn_blocking;
use tracing::info;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{ClientNodeSession, CreateClientNodeSession, Session},
        ServiceError,
    },
    state::AppState,
};

fn create_cookie<'a>(session: &Session) -> Cookie<'a> {
    let expiration = Expiration::DateTime(
        OffsetDateTime::from_unix_timestamp_nanos(session.expires_at.timestamp_nanos() as i128)
            .unwrap(), // out of range error should NOT happen
    );
    Cookie::build(super::SESSION_COOKIE_NAME, session.token.clone())
        .secure(true)
        .http_only(true)
        .path("/")
        .same_site(SameSite::Lax)
        .expires(expiration)
        .finish()
}

/////////////////////////////////////////////////////////////////////////////
// DELETE /api/session
/////////////////////////////////////////////////////////////////////////////

pub(crate) async fn delete_session(
    State(state): State<AppState>,
    Extension(client_session): Extension<ClientSession>,
    jar: CookieJar,
) -> Result<CookieJar, ServiceError> {
    spawn_blocking(move || {
        let db = state.db().new_session()?;
        match &client_session {
            ClientSession::Operator(Operator::Owner(_)) => {
                db.clear_owner_session()?;
            }
            ClientSession::Operator(Operator::ChildNode(child)) => {
                db.clear_client_node_session(&child.node_id)?;
            }
            ClientSession::ParentNode(parent) => {
                db.clear_client_node_session(&parent.node_id)?;
            }
        }
        info!("Deleted a client session: {:?}", client_session);
        Ok(jar.remove(Cookie::named(super::SESSION_COOKIE_NAME)))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/session/owner
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
pub(crate) struct CreateOwnerSession {
    #[validate(required)]
    password: Option<String>,
}

pub(crate) async fn create_owner_session(
    State(state): State<AppState>,
    jar: CookieJar,
    Form(form): Form<CreateOwnerSession>,
) -> Result<(StatusCode, CookieJar, Json<Session>), ServiceError> {
    if let Err(errors) = form.validate() {
        return ("session/owner", errors).into_result();
    }
    spawn_blocking(move || {
        let db = state.db().new_session()?;
        let local_node = db.start_owner_session(
            &form.password.unwrap(), // validated to be Some
            Duration::from_secs(state.config().session_seconds()),
        )?;
        let session = Session {
            token: local_node.owner_session_token.unwrap(),
            expires_at: local_node.owner_session_expires_at.unwrap(),
        };
        let cookie = create_cookie(&session);
        Ok((StatusCode::CREATED, jar.add(cookie), Json(session)))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/session/client-node
/////////////////////////////////////////////////////////////////////////////

pub(crate) async fn create_client_node_session(
    State(state): State<AppState>,
    jar: CookieJar,
    Json(payload): Json<CreateClientNodeSession>,
) -> Result<(StatusCode, CookieJar, Json<ClientNodeSession>), ServiceError> {
    let session = state.create_client_node_session(payload).await?;
    let cookie = create_cookie(&session.session);
    Ok((StatusCode::CREATED, jar.add(cookie), Json(session)))
}
