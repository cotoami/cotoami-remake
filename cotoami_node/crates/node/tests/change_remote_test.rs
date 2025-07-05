use std::{sync::Arc, time::Instant};

use anyhow::{bail, Result};
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use googletest::prelude::*;
use test_log::test;

pub mod common;

#[test(tokio::test)]
async fn change_remote() -> Result<()> {
    // Child
    let child_state = common::new_client_node_state("Child").await?;
    let child_id = child_state.try_get_local_node_id()?;
    let child_owner = Arc::new(child_state.local_node_as_operator()?);

    // Parent
    let server_port = 5107;
    let add_client = AddClient::new(
        child_id,
        Some("connect-password"),
        Some(ChildNodeInput::as_owner()), // as a child
    );
    let (parent_state, shutdown) =
        common::launch_server_node("Parent", server_port, true, add_client).await?;
    let mut parent_ds = parent_state.db().new_session()?;
    let (parent_root_cotonoma, _) = parent_ds.try_get_local_node_root()?;

    // Connect Child/Parent
    common::connect_to_server(
        &child_state,
        format!("http://localhost:{server_port}"),
        "connect-password",
        NodeRole::Child, // client role
    )
    .await?;

    wait_until_parent_registered(&child_state).await?;
    wait_until_parent_root_imported(
        &mut child_state.db().new_session()?,
        &parent_root_cotonoma.uuid,
    )
    .await?;

    /////////////////////////////////////////////////////////////////////////////
    // Command: PostCoto
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::PostCoto {
        input: CotoInput::new("Hello, Cotoami!"),
        post_to: parent_root_cotonoma.uuid,
    }
    .into_request_from(child_owner.clone());
    let posted_coto = child_state.call(request).await?.content::<Coto>()?;

    assert_that!(
        parent_ds.coto(&posted_coto.uuid),
        ok(some(eq(&Coto {
            rowid: 2,
            ..posted_coto
        })))
    );

    /////////////////////////////////////////////////////////////////////////////
    // Command: PostSubcoto
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::PostSubcoto {
        source_coto: posted_coto.uuid,
        input: CotoInput::new("I'm hanging onto you."),
        post_to: Some(parent_root_cotonoma.uuid),
    }
    .into_request_from(child_owner.clone());
    let (subcoto, subito) = child_state.call(request).await?.content::<(Coto, Ito)>()?;

    assert_that!(
        parent_ds.coto(&subcoto.uuid),
        ok(some(eq(&Coto {
            rowid: 3,
            ..subcoto
        })))
    );
    assert_that!(parent_ds.ito(&subito.uuid), ok(some(eq(&subito))));

    shutdown.send(()).ok();

    Ok(())
}

async fn wait_until_parent_registered(child_state: &NodeState) -> Result<()> {
    let start = Instant::now();
    while child_state.parent_services().count() == 0 && start.elapsed().as_secs() < 5 {
        tokio::task::yield_now().await;
    }
    if child_state.parent_services().count() > 0 {
        Ok(())
    } else {
        bail!("No parent services registered.");
    }
}

async fn wait_until_parent_root_imported(
    child_ds: &mut DatabaseSession<'_>,
    parent_root_id: &Id<Cotonoma>,
) -> Result<()> {
    let start = Instant::now();
    let mut parent_root = child_ds.cotonoma(parent_root_id)?;
    while parent_root.is_none() && start.elapsed().as_secs() < 5 {
        tokio::task::yield_now().await;
        parent_root = child_ds.cotonoma(parent_root_id)?;
    }
    if parent_root.is_some() {
        Ok(())
    } else {
        bail!("Parent root has not been imported.");
    }
}
