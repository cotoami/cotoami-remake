use std::sync::Arc;

use anyhow::{anyhow, Result};
use axum::{
    extract::State,
    headers::{Host, Origin},
    http::{
        header::{HeaderMap, HeaderName, HeaderValue},
        Method, Request, StatusCode,
    },
    middleware::Next,
    response::{IntoResponse, Response},
    TypedHeader,
};
use tracing::info;

use super::Config;

const UNPROTECTED_METHODS: &[Method] = &[Method::HEAD, Method::GET, Method::OPTIONS];
const CUSTOM_HEADER: HeaderName = HeaderName::from_static("x-requested-with");

pub(super) async fn protect_from_forgery<B>(
    TypedHeader(origin): TypedHeader<Origin>,
    TypedHeader(host): TypedHeader<Host>,
    State(config): State<Arc<Config>>,
    request: Request<B>,
    next: Next<B>,
) -> Response {
    if UNPROTECTED_METHODS.contains(request.method())
        || is_csrf_safe(&origin, &host, &request, &config)
    {
        next.run(request).await.into_response()
    } else {
        StatusCode::FORBIDDEN.into_response()
    }
}

fn is_csrf_safe<B>(
    origin: &Origin,
    host: &Host,
    request: &Request<B>,
    config: &Arc<Config>,
) -> bool {
    if let Err(e) = check_csrf_safety(origin, host, request, config) {
        info!("CSRF safety check failed: {}", e);
        false
    } else {
        true
    }
}

fn check_csrf_safety<B>(
    origin: &Origin,
    host: &Host,
    request: &Request<B>,
    config: &Arc<Config>,
) -> Result<()> {
    check_custom_header(request.headers())?;
    check_origin(origin, config)?;
    check_host(host, config)?;
    Ok(())
}

/// Prevent requests via HTTP form which can submit requests to any origin.
fn check_custom_header(headers: &HeaderMap<HeaderValue>) -> Result<()> {
    if headers.contains_key(CUSTOM_HEADER) {
        Ok(())
    } else {
        Err(anyhow!("custom header {} is not found", CUSTOM_HEADER))
    }
}

/// Block cross-origin ajax requests.
fn check_origin(origin: &Origin, config: &Arc<Config>) -> Result<()> {
    (origin.is_null()
        || (origin.scheme() == config.url_scheme
            && origin.hostname() == config.url_host
            && origin.port() == config.url_port))
        .then_some(())
        .ok_or(anyhow!("invalid origin header: {}", origin))
}

/// Prevent DNS rebinding attack.
fn check_host(host: &Host, config: &Arc<Config>) -> Result<()> {
    (host.hostname() == config.url_host && host.port() == config.url_port)
        .then_some(())
        .ok_or(anyhow!("invalid host header: {}", host))
}
