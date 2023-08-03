use core::time::Duration;

use anyhow::Result;
use axum::{extract::State, routing::put, Form, Json, Router};
use axum_extra::extract::cookie::{Cookie, CookieJar, Expiration, SameSite};
use chrono::NaiveDateTime;
use time::OffsetDateTime;
use tokio::task::spawn_blocking;
use validator::Validate;

use crate::{
    error::{ApiError, IntoApiResult},
    AppState,
};

pub(super) fn routes() -> Router<AppState> { Router::new().route("/owner", put(auth_as_owner)) }

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
// PUT /api/session/owner
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct AuthAsOwner {
    #[validate(required)]
    password: Option<String>,
}

async fn auth_as_owner(
    jar: CookieJar,
    State(state): State<AppState>,
    Form(form): Form<AuthAsOwner>,
) -> Result<(CookieJar, Json<Session>), ApiError> {
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
        Ok((jar.add(cookie), Json(session)))
    })
    .await?
}
