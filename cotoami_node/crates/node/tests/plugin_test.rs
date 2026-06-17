use std::{
    fs,
    path::{Path, PathBuf},
    process::Command as ProcessCommand,
    time::{Duration, Instant},
};

use anyhow::{bail, Context, Result};
use cotoami_db::prelude::*;
use cotoami_node::prelude::{plugins::PluginEvent, *};
use futures::{Stream, StreamExt};
use googletest::prelude::*;
use tempfile::{tempdir, TempDir};
use test_log::test;

const BASIC_PLUGIN_ID: &str = "app.cotoami.test.basic-plugin";
const BASIC_PLUGIN_NAME: &str = "Basic Test Plugin";
const BASIC_PLUGIN_WASM: &str = "cotoami_node_test_basic_plugin.wasm";

struct TestNode {
    state: NodeState,
    _db_dir: TempDir,
    _plugins_dir: TempDir,
}

#[test(tokio::test)]
async fn basic_plugin_system_features() -> Result<()> {
    let wasm = build_basic_plugin()?;
    let node = new_test_node_with_plugin(&wasm).await?;
    let mut events = node.state.pubsub().events().subscribe(None::<()>);

    let registered = wait_for_plugin_event(&mut events, |event| {
        matches!(
            event,
            PluginEvent::Registered {
                identifier,
                name,
                ..
            } if identifier == BASIC_PLUGIN_ID && name == BASIC_PLUGIN_NAME
        )
    })
    .await?;
    assert_that!(
        registered,
        pat!(PluginEvent::Registered {
            identifier: eq(BASIC_PLUGIN_ID),
            name: eq(BASIC_PLUGIN_NAME),
            ..
        })
    );
    let agent_node_id = wait_for_agent_node_id(&node.state).await?;
    tokio::time::sleep(Duration::from_millis(10)).await;
    let mut ds = node.state.db().new_session()?;
    assert_that!(
        ds.node(&agent_node_id.parse()?)?,
        some(pat!(Node {
            name: eq(BASIC_PLUGIN_NAME),
            ..
        }))
    );
    let (root_cotonoma, _) = ds.try_get_local_node_root()?;
    drop(ds);

    let opr = node.state.local_node_as_operator()?;
    let ds = node.state.db().new_session()?;
    let (posted, log) = ds.post_coto(
        &CotoInput::new("#plugin-test hello"),
        &root_cotonoma.uuid,
        &opr,
    )?;
    node.state.pubsub().publish_change(log);
    drop(ds);
    assert_that!(posted.content, some(eq("#plugin-test hello")));

    let reply = wait_for_reply_coto(&node.state, "plugin reply: hello").await?;
    assert_that!(
        reply,
        pat!(Coto {
            posted_by_id: eq(&agent_node_id.parse()?),
            content: some(eq("plugin reply: hello")),
            posted_in_id: some(eq(&root_cotonoma.uuid)),
            ..
        })
    );

    node.state.shutdown().await;
    Ok(())
}

async fn wait_for_agent_node_id(state: &NodeState) -> Result<String> {
    let start = Instant::now();
    loop {
        if let Some(agent_node_id) = state.read_plugins().configs().agent_node_id(BASIC_PLUGIN_ID) {
            return Ok(agent_node_id);
        }

        if start.elapsed() > Duration::from_secs(10) {
            bail!("timed out waiting for plugin agent config");
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

async fn wait_for_plugin_event<F>(
    events: &mut (impl Stream<Item = LocalNodeEvent> + Unpin),
    predicate: F,
) -> Result<PluginEvent>
where
    F: Fn(&PluginEvent) -> bool,
{
    tokio::time::timeout(Duration::from_secs(10), async {
        while let Some(event) = events.next().await {
            let LocalNodeEvent::PluginEvent(plugin_event) = event else {
                continue;
            };
            if predicate(&plugin_event) {
                return Ok(plugin_event);
            }
        }
        bail!("local node event stream ended before expected plugin event")
    })
    .await?
}

async fn new_test_node_with_plugin(wasm: &Path) -> Result<TestNode> {
    let db_dir = tempdir()?;
    let plugins_dir = tempdir()?;
    fs::copy(wasm, plugins_dir.path().join(BASIC_PLUGIN_WASM))?;

    let mut config = NodeConfig::new_standalone(
        Some(db_dir.path().to_string_lossy().to_string()),
        Some("plugin test node".to_string()),
    );
    config.plugins_dir = Some(plugins_dir.path().to_string_lossy().to_string());
    config.owner_password = Some("master-password".to_string());

    let state = NodeState::new(config).await?;
    Ok(TestNode {
        state,
        _db_dir: db_dir,
        _plugins_dir: plugins_dir,
    })
}

async fn wait_for_reply_coto(state: &NodeState, content: &str) -> Result<Coto> {
    let start = Instant::now();
    loop {
        let state = state.clone();
        let query = content.to_string();
        let cotos = tokio::task::spawn_blocking(move || {
            let mut ds = state.db().new_session()?;
            ds.search_cotos(&query, Scope::All, false, 10, 0)
        })
        .await??;
        if let Some(coto) = cotos
            .rows
            .into_iter()
            .find(|coto| coto.content.as_deref() == Some(content))
        {
            return Ok(coto);
        }

        if start.elapsed() > Duration::from_secs(10) {
            bail!("timed out waiting for plugin reply coto: {content}");
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

fn build_basic_plugin() -> Result<PathBuf> {
    let fixture_dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/basic_plugin");
    let status = ProcessCommand::new("cargo")
        .arg("build")
        .arg("--release")
        .current_dir(&fixture_dir)
        .status()
        .context("failed to start fixture plugin build")?;
    if !status.success() {
        bail!("fixture plugin build failed with status {status}");
    }

    let wasm = fixture_dir
        .join("target")
        .join("wasm32-unknown-unknown")
        .join("release")
        .join(BASIC_PLUGIN_WASM);
    if !wasm.is_file() {
        bail!("fixture plugin wasm was not built: {}", wasm.display());
    }
    Ok(wasm)
}
