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

    /////////////////////////////////////////////////////////////////////////////
    // When: add a loop
    /////////////////////////////////////////////////////////////////////////////

    let (coto3, _) = ds.post_coto("coto3", None, &root, &opr)?;
    let _ = ds.create_link(&coto2.uuid, &coto3.uuid, None, None, None, &opr)?;
    let _ = ds.create_link(&coto3.uuid, &coto1.uuid, None, None, None, &opr)?;

    let graph = ds.graph(root.clone(), true)?;
    assert_eq!(
        Dot::new(&graph.into_petgraph()).to_string(),
        indoc! {r#"
            digraph {
                0 [ label = "<My Node>" ]
                1 [ label = "coto1" ]
                2 [ label = "coto2" ]
                3 [ label = "<cotonoma1>" ]
                4 [ label = "coto3" ]
                0 -> 1 [ label = "foo" ]
                1 -> 2 [ label = "" ]
                1 -> 3 [ label = "" ]
                2 -> 4 [ label = "" ]
                4 -> 1 [ label = "" ]
            }
            "#, 
        }
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: until cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let (coto4, _) = ds.post_coto("coto4", None, &root, &opr)?;
    let _ = ds.create_link(&cotonoma1.coto_id, &coto4.uuid, None, None, None, &opr)?;

    // until_cotonoma = true
    let graph2 = ds.graph(root.clone(), true)?;
    assert_eq!(
        Dot::new(&graph2.into_petgraph()).to_string(),
        Dot::new(&graph.into_petgraph()).to_string()
    );

    // until_cotonoma = false
    let graph = ds.graph(root.clone(), false)?;
    assert_eq!(
        Dot::new(&graph.into_petgraph()).to_string(),
        indoc! {r#"
            digraph {
                0 [ label = "<My Node>" ]
                1 [ label = "coto1" ]
                2 [ label = "coto2" ]
                3 [ label = "<cotonoma1>" ]
                4 [ label = "coto3" ]
                5 [ label = "coto4" ]
                0 -> 1 [ label = "foo" ]
                1 -> 2 [ label = "" ]
                1 -> 3 [ label = "" ]
                2 -> 4 [ label = "" ]
                4 -> 1 [ label = "" ]
                3 -> 5 [ label = "" ]
            }
            "#, 
        }
    );

    Ok(())
}
