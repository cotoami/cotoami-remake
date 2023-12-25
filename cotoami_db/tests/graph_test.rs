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
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.root_cotonoma()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: add a child
    /////////////////////////////////////////////////////////////////////////////

    let (coto1, _) = ds.post_coto("coto1", None, &root, &opr)?;
    let _ = ds.create_link(&root.coto_id, &coto1.uuid, Some("foo"), None, None, &opr)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    let graph = ds.graph(root.clone(), true)?;
    assert_eq!(
        Dot::new(&graph.into_petgraph()).to_string(),
        indoc! {r#"
            digraph {
                0 [ label = "<My Node>" ]
                1 [ label = "coto1" ]
                0 -> 1 [ label = "foo" ]
            }
            "#, 
        }
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: add grandchildren
    /////////////////////////////////////////////////////////////////////////////

    let (coto2, _) = ds.post_coto("coto2", None, &root, &opr)?;
    let _ = ds.create_link(&coto1.uuid, &coto2.uuid, None, None, None, &opr)?;

    let ((cotonoma1, _), _) = ds.post_cotonoma("cotonoma1", &root, &opr)?;
    let _ = ds.create_link(&coto1.uuid, &cotonoma1.coto_id, None, None, None, &opr)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    let graph = ds.graph(root.clone(), true)?;
    assert_eq!(
        Dot::new(&graph.into_petgraph()).to_string(),
        indoc! {r#"
            digraph {
                0 [ label = "<My Node>" ]
                1 [ label = "coto1" ]
                2 [ label = "coto2" ]
                3 [ label = "<cotonoma1>" ]
                0 -> 1 [ label = "foo" ]
                1 -> 2 [ label = "" ]
                1 -> 3 [ label = "" ]
            }
            "#, 
        }
    );

    Ok(())
}
