use core::time::Duration;

use anyhow::Result;
use axum::{
    extract::State,
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

#[derive(serde::Serialize, serde::Deserialize)]
pub(crate) struct Session {
    pub token: String,
    pub expires_at: NaiveDateTime, // UTC
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
        let db = state.db.create_session()?;
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
        let db = state.db.create_session()?;
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
// PUT /api/session/child
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize, serde::Deserialize)]
pub(crate) struct CreateChildSession {
    pub password: String,
    pub new_password: Option<String>,
    pub child: Node,
}

#[derive(serde::Serialize, serde::Deserialize)]
pub(crate) struct ChildSessionCreated {
    pub session: Session,
    pub parent: Node,
}

async fn create_child_session(
    State(state): State<AppState>,
    jar: CookieJar,
    Json(payload): Json<CreateChildSession>,
) -> Result<(StatusCode, CookieJar, Json<ChildSessionCreated>), ApiError> {
    spawn_blocking(move || {
        let mut db = state.db.create_session()?;

        // start session
        let child_node = db.start_child_session(
            &payload.child.uuid,
            &payload.password, // validated to be Some
            Duration::from_secs(state.config.session_seconds()),
        )?;
        let session = Session {
            token: child_node.session_token.unwrap(),
            expires_at: child_node.session_expires_at.unwrap(),
        };
        let cookie = create_cookie(&session);

        // change password
        if let Some(new_password) = payload.new_password {
            db.change_child_password(&payload.child.uuid, &new_password)?;
        }

        // import the child node
        if let Some((_, changelog)) = db.import_node(&payload.child)? {
            state.publish_change(changelog)?;
        }

        // make response body
        let (_, parent) = db.get_local_node()?.unwrap();
        let result = ChildSessionCreated { session, parent };

        Ok((StatusCode::CREATED, jar.add(cookie), Json(result)))
    })
    .await?
}
