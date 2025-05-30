use std::sync::Arc;

use anyhow::{anyhow, Result};
use axum::{
    extract::Request,
    http::{
        header::{HeaderMap, HeaderName, HeaderValue},
        Method, StatusCode,
    },
    middleware::Next,
    response::{IntoResponse, Response},
    Extension,
};
use axum_extra::headers::{HeaderMapExt, Host, Origin};
use tracing::info;

use crate::web::ServerConfig;

const UNPROTECTED_METHODS: &[Method] = &[Method::HEAD, Method::GET, Method::OPTIONS];

// https://github.com/rust-lang/rust-clippy/issues/9776
#[allow(clippy::declare_interior_mutable_const)]
pub(crate) const CUSTOM_HEADER: HeaderName = HeaderName::from_static("x-requested-with");

pub(super) async fn protect_from_forgery(
    Extension(config): Extension<Arc<ServerConfig>>,
    request: Request,
    next: Next,
) -> Response {
    if UNPROTECTED_METHODS.contains(request.method()) || is_csrf_safe(&request, &config) {
        next.run(request).await
    } else {
        StatusCode::FORBIDDEN.into_response()
    }
}

fn is_csrf_safe<B>(request: &Request<B>, config: &Arc<ServerConfig>) -> bool {
    if let Err(e) = check_csrf_safety(request, config) {
        info!("CSRF safety check failed: {}", e);
        false
    } else {
        true
    }
}

fn check_csrf_safety<B>(request: &Request<B>, config: &Arc<ServerConfig>) -> Result<()> {
    check_custom_header(request.headers())?;
    check_origin(request.headers(), config)?;
    check_host(request.headers(), config)?;
    Ok(())
}

/// Prevent requests via HTTP form which can submit requests to any origin.
fn check_custom_header(headers: &HeaderMap<HeaderValue>) -> Result<()> {
    if headers.contains_key(CUSTOM_HEADER) {
        Ok(())
    } else {
        // https://github.com/rust-lang/rust-clippy/issues/5812
        #[allow(clippy::borrow_interior_mutable_const)]
        Err(anyhow!("custom header {} was missing", CUSTOM_HEADER))
    }
}

/// Block cross-origin ajax requests.
fn check_origin(headers: &HeaderMap<HeaderValue>, config: &Arc<ServerConfig>) -> Result<()> {
    if let Some(origin) = headers.typed_get::<Origin>() {
        (origin.is_null()
            || (origin.scheme() == config.url_scheme
                && origin.hostname() == config.url_host
                && origin.port() == config.url_port))
            .then_some(())
            .ok_or(anyhow!("invalid origin header: {}", origin))
    } else {
        Ok(())
    }
}

/// Prevent DNS rebinding attack.
fn check_host(headers: &HeaderMap<HeaderValue>, config: &Arc<ServerConfig>) -> Result<()> {
    if let Some(host) = headers.typed_get::<Host>() {
        (host.hostname() == config.url_host && host.port() == config.url_port)
            .then_some(())
            .ok_or(anyhow!("invalid host header: {}", host))
    } else {
        Err(anyhow!("host header was missing"))
    }
}
