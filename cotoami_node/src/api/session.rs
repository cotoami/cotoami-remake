use core::time::Duration;

use anyhow::Result;
use axum::{
    extract::{Path, State},
    http::StatusCode,
    middleware,
    routing::{delete, put},
    Extension, Form, Json, Router,
};
use axum_extra::extract::cookie::{Cookie, CookieJar, Expiration, SameSite};
use chrono::NaiveDateTime;
use cotoami_db::prelude::*;
use time::OffsetDateTime;
use tokio::task::spawn_blocking;
use tracing::info;
use validator::Validate;

use crate::{
    error::{ApiError, IntoApiResult},
    AppState,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", delete(delete_session))
        .route_layer(middleware::from_fn(super::require_session))
        .route("/owner", put(create_owner_session))
        .route("/child", put(create_child_session))
}

#[derive(serde::Serialize)]
struct Session {
    token: String,
    expires_at: NaiveDateTime, // UTC
}

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

async fn delete_session(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    jar: CookieJar,
) -> Result<CookieJar, ApiError> {
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        match &operator {
            Operator::Owner(_) => db.clear_owner_session()?,
            Operator::ChildNode(child) => db.clear_child_session(&child.node_id)?,
        }
        info!("Deleted a session as: {:?}", operator);
        Ok(jar.remove(Cookie::named(super::SESSION_COOKIE_NAME)))
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
    State(state): State<AppState>,
    jar: CookieJar,
    Form(form): Form<CreateOwnerSession>,
) -> Result<(StatusCode, CookieJar, Json<Session>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("session/owner", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let local_node = db.start_owner_session(
            &form.password.unwrap(), // validated to be Some
            Duration::from_secs(state.config.session_seconds()),
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
// PUT /api/session/child/:node_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct CreateChildSession {
    #[validate(required)]
    password: Option<String>,
}

async fn create_child_session(
    State(state): State<AppState>,
    Path(node_id): Path<Id<Node>>,
    jar: CookieJar,
    Form(form): Form<CreateChildSession>,
) -> Result<(StatusCode, CookieJar, Json<Session>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("session/child", errors).into_result();
    }
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;
        let child_node = db.start_child_session(
            &node_id,
            &form.password.unwrap(), // validated to be Some
            Duration::from_secs(state.config.session_seconds()),
        )?;
        let session = Session {
            token: child_node.session_token.unwrap(),
            expires_at: child_node.session_expires_at.unwrap(),
        };
        let cookie = create_cookie(&session);
        Ok((StatusCode::CREATED, jar.add(cookie), Json(session)))
    })
    .await?
}
