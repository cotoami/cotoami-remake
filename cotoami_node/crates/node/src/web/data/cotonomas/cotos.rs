use std::sync::Arc;

use anyhow::Result;
use axum::{
    extract::{Json, Path, Query, State},
    http::StatusCode,
    routing::{get, post},
    Extension, Router,
};
use axum_extra::TypedHeader;
use cotoami_db::prelude::*;
use validator::Validate;

use crate::{
    service::{
        error::IntoServiceResult,
        models::{GeolocatedCotos, PaginatedCotos, Pagination},
        ServiceError,
    },
    state::NodeState,
    web::{Accept, Content},
};

pub(super) fn routes() -> Router<NodeState> {
    Router::new()
        .route("/", get(recent_cotos).post(post_coto))
        .route("/cotonomas", get(recent_cotonoma_cotos))
        .route("/repost", post(repost))
        .route("/geolocated", get(geolocated_cotos))
        .route("/search/{query}", get(search_cotos))
        .route("/search/cotonomas/{query}", get(search_cotonoma_cotos))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(query): Query<CotosQuery>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    let pagination = query.pagination();
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .recent_cotos(query.scope(cotonoma_id), false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/cotonomas
/////////////////////////////////////////////////////////////////////////////

async fn recent_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(query): Query<CotosQuery>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    let pagination = query.pagination();
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .recent_cotos(query.scope(cotonoma_id), true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

#[derive(Debug, serde::Deserialize)]
struct CotosQuery {
    #[serde(default)]
    page: i64,
    page_size: Option<i64>,
    #[serde(default)]
    recursive: bool,
    depth: Option<usize>,
}

impl CotosQuery {
    fn pagination(&self) -> Pagination {
        Pagination {
            page: self.page,
            page_size: self.page_size,
        }
    }

    // If both `recursive=true` and `depth` are given, use `depth`.
    fn cotonoma_scope(&self) -> CotonomaScope {
        if let Some(depth) = self.depth {
            CotonomaScope::Depth(depth)
        } else if self.recursive {
            CotonomaScope::Recursive
        } else {
            CotonomaScope::Local
        }
    }

    fn scope(&self, cotonoma_id: Id<Cotonoma>) -> Scope {
        Scope::Cotonoma((cotonoma_id, self.cotonoma_scope()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn depth_takes_precedence_over_recursive() {
        let query = CotosQuery {
            page: 0,
            page_size: None,
            recursive: true,
            depth: Some(2),
        };
        assert_eq!(query.cotonoma_scope(), CotonomaScope::Depth(2));
    }

    #[test]
    fn recursive_maps_to_recursive_scope() {
        let query = CotosQuery {
            page: 0,
            page_size: None,
            recursive: true,
            depth: None,
        };
        assert_eq!(query.cotonoma_scope(), CotonomaScope::Recursive);
    }

    #[test]
    fn default_maps_to_local_scope() {
        let query = CotosQuery {
            page: 0,
            page_size: None,
            recursive: false,
            depth: None,
        };
        assert_eq!(query.cotonoma_scope(), CotonomaScope::Local);
    }
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/cotonomas/:cotonoma_id/cotos
/////////////////////////////////////////////////////////////////////////////

async fn post_coto(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Json(input): Json<CotoInput<'static>>,
) -> Result<(StatusCode, Content<Coto>), ServiceError> {
    state
        .post_coto(input, cotonoma_id, Arc::new(operator))
        .await
        .map(|coto| (StatusCode::CREATED, Content(coto, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/data/cotonomas/:cotonoma_id/cotos/repost
/////////////////////////////////////////////////////////////////////////////

async fn repost(
    State(state): State<NodeState>,
    Extension(operator): Extension<Operator>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Json(coto_id): Json<Id<Coto>>,
) -> Result<(StatusCode, Content<(Coto, Coto)>), ServiceError> {
    state
        .repost(coto_id, cotonoma_id, Arc::new(operator))
        .await
        .map(|repost| (StatusCode::CREATED, Content(repost, accept)))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/geolocated
/////////////////////////////////////////////////////////////////////////////

async fn geolocated_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path(cotonoma_id): Path<Id<Cotonoma>>,
    Query(cotos_query): Query<CotosQuery>,
) -> Result<Content<GeolocatedCotos>, ServiceError> {
    state
        .geolocated_cotos(cotos_query.scope(cotonoma_id))
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((cotonoma_id, query)): Path<(Id<Cotonoma>, String)>,
    Query(cotos_query): Query<CotosQuery>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    let pagination = cotos_query.pagination();
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .search_cotos(query, cotos_query.scope(cotonoma_id), false, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/data/cotonomas/:cotonoma_id/cotos/cotonomas/search/:query
/////////////////////////////////////////////////////////////////////////////

async fn search_cotonoma_cotos(
    State(state): State<NodeState>,
    TypedHeader(accept): TypedHeader<Accept>,
    Path((cotonoma_id, query)): Path<(Id<Cotonoma>, String)>,
    Query(cotos_query): Query<CotosQuery>,
) -> Result<Content<PaginatedCotos>, ServiceError> {
    let pagination = cotos_query.pagination();
    if let Err(errors) = pagination.validate() {
        return errors.into_result();
    }
    state
        .search_cotos(query, cotos_query.scope(cotonoma_id), true, pagination)
        .await
        .map(|cotos| Content(cotos, accept))
}
