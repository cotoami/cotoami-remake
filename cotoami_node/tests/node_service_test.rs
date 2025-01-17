use anyhow::Result;
use cotoami_db::prelude::*;
use cotoami_node::prelude::*;
use futures::Stream;
use googletest::prelude::*;
use tempfile::tempdir;

async fn test_node_service<S, C>(service: S, ds: &mut DatabaseSession<'_>, changes: C) -> Result<()>
where
    S: NodeService,
    C: Stream<Item = ChangelogEntry>,
{
    /////////////////////////////////////////////////////////////////////////////
    // Command: LocalNode
    /////////////////////////////////////////////////////////////////////////////

    let request = Command::LocalNode.into_request();
    let request_id = *request.id();
    let response = service.call(request).await?;
    assert_that!(response.id(), eq(&request_id));

    let node = response.content::<Node>()?;
    assert_that!(
        node,
        pat!(Node {
            name: eq("Cotoami")
        })
    );

    Ok(())
}

fn default_config() -> Result<NodeConfig> {
    let db_dir = tempdir()?;
    Ok(NodeConfig::new_standalone(
        Some(db_dir.path().to_string_lossy().into()),
        Some("Cotoami".into()),
    ))
}

#[tokio::test]
async fn local_node() -> Result<()> {
    let config = default_config()?;
    let state = NodeState::new(config).await?;
    let mut ds = state.db().new_session()?;
    let changes = state.pubsub().changes().subscribe(None::<()>);

    test_node_service(state.clone(), &mut ds, changes).await
}
