use anyhow::Result;
use axum::{
    extract::{Path, State},
    http::StatusCode,
    middleware,
    routing::{get, put},
    Extension, Form, Json, Router,
};
use cotoami_db::prelude::*;
use tokio::task::spawn_blocking;
use tracing::{debug, info};
use validator::Validate;

use crate::{
    api::require_session,
    client::{EventLoopError, Server},
    error::{ApiError, IntoApiResult},
    AppState, ChangePub, ParentConn,
};

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route("/", get(all_parents).post(add_parent_node))
        .route("/:node_id", put(update_parent_node))
        .route("/:node_id/fork", put(fork_from_parent))
        .layer(middleware::from_fn(require_session))
}

/////////////////////////////////////////////////////////////////////////////
// GET /api/nodes/parents
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct Parent {
    node: Node,
    not_connected: Option<NotConnected>,
}

impl Parent {
    fn new(node: Node, parent_conn: &ParentConn) -> Self {
        let not_connected = match parent_conn {
            ParentConn::Disabled => Some(NotConnected::Disabled),
            ParentConn::InitFailed(e) => Some(NotConnected::InitFailed(e.to_string())),
            ParentConn::Connected {
                event_loop_state, ..
            } => {
                let state = event_loop_state.read();
                if state.is_running() {
                    None // connected
                } else if state.is_disabled() {
                    Some(NotConnected::Disabled)
                } else if state.is_connecting() {
                    let details =
                        if let Some(EventLoopError::StreamFailed(e)) = state.error.as_ref() {
                            Some(e.to_string())
                        } else {
                            None
                        };
                    Some(NotConnected::Connecting(details))
                } else if let Some(error) = state.error.as_ref() {
                    match error {
                        EventLoopError::StreamFailed(e) => {
                            Some(NotConnected::StreamFailed(e.to_string()))
                        }
                        EventLoopError::EventHandlingFailed(e) => {
                            Some(NotConnected::EventHandlingFailed(e.to_string()))
                        }
                    }
                } else {
                    Some(NotConnected::Unknown)
                }
            }
        };
        Self {
            node,
            not_connected,
        }
    }
}

#[derive(serde::Serialize)]
#[serde(tag = "reason", content = "details")]
enum NotConnected {
    Disabled,
    Connecting(Option<String>),
    InitFailed(String),
    StreamFailed(String),
    EventHandlingFailed(String),
    Unknown,
}

async fn all_parents(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
) -> Result<Json<Vec<Parent>>, ApiError> {
    spawn_blocking(move || {
        let conns = state.parent_conns.read();
        let mut db = state.db.new_session()?;
        let nodes = db
            .all_parent_nodes(&operator)?
            .into_iter()
            .map(|(_, node)| {
                let conn = conns.get(&node.uuid).unwrap_or_else(|| unreachable!());
                Parent::new(node, conn)
            })
            .collect();
        Ok(Json(nodes))
    })
    .await?
}

/////////////////////////////////////////////////////////////////////////////
// POST /api/nodes/parents
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct AddParentNode {
    #[validate(required, url)]
    url_prefix: Option<String>,

    #[validate(required)]
    password: Option<String>,

    /// Set true if you want to turn this node into a replica of the parent node,
    /// which means the root cotonoma will be changed to that of the parent.
    replicate: Option<bool>,
}

async fn add_parent_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Form(form): Form<AddParentNode>,
) -> Result<(StatusCode, Json<Parent>), ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/parent", errors).into_result();
    }

    // Get the local node
    let db = state.db.clone();
    let local_node = spawn_blocking(move || db.new_session()?.local_node()).await??;

    // Attempt to log in to the parent node
    let password = form.password.unwrap();
    let mut server = Server::new(form.url_prefix.unwrap())?;
    let child_session = server
        .create_child_session(
            password.clone(),
            None, // TODO
            &local_node,
        )
        .await?;
    info!("Successfully logged in to {}", server.url_prefix());

    // Register the parent node
    let (config, db, pubsub) = (state.config.clone(), state.db.clone(), state.pubsub.clone());
    let op = operator.clone();
    let parent_id = child_session.parent.uuid;
    let url_prefix = server.url_prefix().to_string();
    let parent_node = spawn_blocking(move || {
        let owner_password = config.owner_password();
        let mut db = db.new_session()?;

        // Import the parent node data, which is required for registering a [ParentNode]
        if let Some((_, changelog)) = db.import_node(&child_session.parent)? {
            pubsub.lock().publish_change(changelog)?;
        }

        // Register a [ParentNode] and save the password into it
        db.register_parent_node(&parent_id, &url_prefix, &op)?;
        db.save_parent_password(&parent_id, &password, owner_password, &op)?;

        // Get the imported node data
        let node = db.node(&parent_id)?.unwrap_or_else(|| unreachable!());
        Ok::<_, ApiError>(node)
    })
    .await??;
    info!("Parent node [{}] registered.", parent_node.name);

    // Import changes from the parent
    server
        .import_changes(&state.db, &state.pubsub, parent_node.uuid)
        .await?;

    // Create a link to the parent root cotonoma or become a replica of the parent
    if let Some(parent_cotonoma_id) = parent_node.root_cotonoma_id {
        let (db, pubsub) = (state.db.clone(), state.pubsub.clone());
        if form.replicate.unwrap_or(false) {
            let parent_node_name = parent_node.name.clone();
            let _ = spawn_blocking(move || {
                let db = db.new_session()?;
                let (local_node, change) = db.set_root_cotonoma(&parent_cotonoma_id, &operator)?;
                pubsub.lock().publish_change(change)?;
                info!("This node is now replicating [{}].", parent_node_name);
                Ok::<_, ApiError>(local_node)
            })
            .await??;
        } else {
            let _ = spawn_blocking(move || {
                let mut db = db.new_session()?;
                if let Some((_, local_root)) = db.root_cotonoma()? {
                    let (_, parent_root) = db.cotonoma_or_err(&parent_cotonoma_id)?;
                    let (link, change) = db.create_link(
                        &local_root.uuid,
                        &parent_root.uuid,
                        None,
                        None,
                        None,
                        &operator,
                    )?;
                    pubsub.lock().publish_change(change)?;
                    info!(
                        "A link to a parent root cotonoma [{}] has been created: {}",
                        parent_root
                            .name_as_cotonoma()
                            .unwrap_or_else(|| unreachable!()),
                        link.uuid
                    );
                }
                Ok::<_, ApiError>(())
            })
            .await??;
        }
    }

    // Create an event stream
    let event_loop = server
        .create_event_loop(parent_node.uuid, &state.db, &state.pubsub)
        .await?;

    // Store the parent connection
    state.put_parent_conn(&parent_node.uuid, child_session.session, event_loop);

    // Make response body
    let parent = {
        let conns = state.parent_conns.read();
        let conn = conns
            .get(&parent_node.uuid)
            .unwrap_or_else(|| unreachable!());
        Parent::new(parent_node, conn)
    };

    Ok((StatusCode::CREATED, Json(parent)))
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/parents/:node_id
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Deserialize, Validate)]
struct UpdateParentNode {
    disabled: Option<bool>,
    // TODO: url_prefix
}

async fn update_parent_node(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
    Form(form): Form<UpdateParentNode>,
) -> Result<StatusCode, ApiError> {
    if let Err(errors) = form.validate() {
        return ("nodes/parent", errors).into_result();
    }
    if let Some(disabled) = form.disabled {
        set_parent_disabled(node_id, disabled, &state, operator).await?;
    }
    Ok(StatusCode::OK)
}

async fn set_parent_disabled(
    parent_id: Id<Node>,
    disabled: bool,
    state: &AppState,
    operator: Operator,
) -> Result<()> {
    // Update the attribute of the parent node
    let db = state.db.clone();
    let (local_node, parent_node) = spawn_blocking(move || {
        let mut db = db.new_session()?;
        Ok::<_, anyhow::Error>((
            db.local_node()?,
            db.set_parent_disabled(&parent_id, disabled, &operator)?,
        ))
    })
    .await??;

    // Disconnect from the parent
    if disabled {
        debug!("Stopping the event loop for {}", parent_id);
        state.parent_conn(&parent_id)?.disable_event_loop();

    // Or connect to the parent again (if not forked from the parent)
    } else if !parent_node.forked {
        if state
            .parent_conn(&parent_id)?
            .restart_event_loop_if_possible()
        {
            debug!("Restarting the event loop for {}", parent_id);
        } else {
            debug!("Creating a new parent connection for {}", parent_id);
            let conn = ParentConn::connect(
                &parent_node,
                &local_node,
                &state.config,
                &state.db,
                &state.pubsub,
            )
            .await;
            state.parent_conns.write().insert(parent_id, conn);
        }
    }

    Ok(())
}

/////////////////////////////////////////////////////////////////////////////
// PUT /api/nodes/parents/:node_id/fork
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
struct Forked {
    affected: usize,
}

async fn fork_from_parent(
    State(state): State<AppState>,
    Extension(operator): Extension<Operator>,
    Path(node_id): Path<Id<Node>>,
) -> Result<Json<Forked>, ApiError> {
    state.parent_conn(&node_id)?.disable_event_loop();

    let (affected, change) = spawn_blocking(move || {
        let db = state.db.new_session()?;
        db.fork_from(&node_id, &operator)
    })
    .await??;
    state.pubsub.lock().publish_change(change)?;

    Ok(Json(Forked { affected }))
}
