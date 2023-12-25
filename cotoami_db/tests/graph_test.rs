use anyhow::Result;
use indoc::indoc;
use petgraph::dot::Dot;

pub mod common;

#[test]
fn graph() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let operator = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.root_cotonoma()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: single coto
    /////////////////////////////////////////////////////////////////////////////
    let (coto, _) = ds.post_coto("hello", None, &root_cotonoma, &operator)?;
    let _ = ds.create_link(
        &root_cotonoma.coto_id,
        &coto.uuid,
        Some("foo"),
        None,
        Some(&root_cotonoma),
        &operator,
    )?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    let graph = ds.graph(root_cotonoma, true)?;
    assert_eq!(
        Dot::new(&graph.into_petgraph()).to_string(),
        indoc! {r#"
            digraph {
                0 [ label = "<My Node>" ]
                1 [ label = "hello" ]
                0 -> 1 [ label = "foo" ]
            }
            "#, 
        }
    );

    Ok(())
}
