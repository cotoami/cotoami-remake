use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;
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
    let (root, root_coto) = ds.local_node_root()?.unwrap();

    let connect = {
        let ds = db.new_session()?;
        let opr = opr.clone();
        move |input: &ItoInput| -> Result<Ito> {
            let (ito, _) = ds.create_ito(input, &opr)?;
            Ok(ito)
        }
    };

    /////////////////////////////////////////////////////////////////////////////
    // When: add a child
    /////////////////////////////////////////////////////////////////////////////

    let (coto1, _) = ds.post_coto(&CotoInput::new("coto1"), &root.uuid, &opr)?;
    let ito1 = connect(&ItoInput::new(root_coto.uuid, coto1.uuid).description("foo"))?;

    let expected_dot = indoc! {r#"
        digraph {
            0 [ label = "<My Node>" ]
            1 [ label = "coto1" ]
            0 -> 1 [ label = "foo" ]
        }
    "#};
    assert_graph(ds.graph(root_coto.clone(), true)?, expected_dot);
    assert_graph(ds.graph_by_cte(root_coto.clone(), true)?, expected_dot);

    assert_that!(
        ds.ancestors_of(&coto1.uuid)?,
        elements_are![(
            unordered_elements_are![pat!(Ito {
                uuid: eq(&ito1.uuid),
                description: some(eq(&"foo")),
                ..
            })],
            unordered_elements_are![pat!(Coto {
                uuid: eq(&root_coto.uuid),
                ..
            })]
        )]
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: add grandchildren
    /////////////////////////////////////////////////////////////////////////////

    let (coto2, _) = ds.post_coto(&CotoInput::new("coto2"), &root.uuid, &opr)?;
    let ito2 = connect(&ItoInput::new(coto1.uuid, coto2.uuid))?;

    let ((cotonoma1, _), _) = ds.post_cotonoma(&CotonomaInput::new("cotonoma1"), &root, &opr)?;
    let _ito3 = connect(&ItoInput::new(coto1.uuid, cotonoma1.coto_id))?;

    let graph = ds.graph(root_coto.clone(), true)?;
    let graph_by_cte = ds.graph_by_cte(root_coto.clone(), true)?;

    graph.assert_itos_sorted();
    graph_by_cte.assert_itos_sorted();

    let expected_dot = indoc! {r#"
        digraph {
            0 [ label = "<My Node>" ]
            1 [ label = "coto1" ]
            2 [ label = "coto2" ]
            3 [ label = "<cotonoma1>" ]
            0 -> 1 [ label = "foo" ]
            1 -> 2 [ label = "" ]
            1 -> 3 [ label = "" ]
        }
    "#};
    assert_graph(graph, expected_dot);
    assert_graph(graph_by_cte, expected_dot);

    assert_that!(
        ds.ancestors_of(&coto2.uuid)?,
        elements_are![
            (
                unordered_elements_are![pat!(Ito {
                    uuid: eq(&ito2.uuid),
                    ..
                })],
                unordered_elements_are![pat!(Coto {
                    uuid: eq(&coto1.uuid),
                    ..
                })]
            ),
            (
                unordered_elements_are![pat!(Ito {
                    uuid: eq(&ito1.uuid),
                    description: some(eq(&"foo")),
                    ..
                })],
                unordered_elements_are![pat!(Coto {
                    uuid: eq(&root_coto.uuid),
                    ..
                })]
            )
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: add a loop
    /////////////////////////////////////////////////////////////////////////////

    let (coto3, _) = ds.post_coto(&CotoInput::new("coto3"), &root.uuid, &opr)?;
    let ito4 = connect(&ItoInput::new(coto2.uuid, coto3.uuid))?;
    let ito5 = connect(&ItoInput::new(coto3.uuid, coto1.uuid))?;

    let expected_dot = indoc! {r#"
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
    "#};
    assert_graph(ds.graph(root_coto.clone(), true)?, expected_dot);
    assert_graph(ds.graph_by_cte(root_coto.clone(), true)?, expected_dot);

    assert_that!(
        ds.ancestors_of(&coto3.uuid)?,
        elements_are![
            (
                unordered_elements_are![pat!(Ito {
                    uuid: eq(&ito4.uuid),
                    ..
                })],
                unordered_elements_are![pat!(Coto {
                    uuid: eq(&coto2.uuid),
                    ..
                })]
            ),
            (
                unordered_elements_are![pat!(Ito {
                    uuid: eq(&ito2.uuid),
                    ..
                })],
                unordered_elements_are![pat!(Coto {
                    uuid: eq(&coto1.uuid),
                    ..
                })]
            ),
            (
                unordered_elements_are![
                    pat!(Ito {
                        uuid: eq(&ito1.uuid),
                        description: some(eq(&"foo")),
                        ..
                    }),
                    pat!(Ito {
                        uuid: eq(&ito5.uuid),
                        ..
                    })
                ],
                unordered_elements_are![pat!(Coto {
                    uuid: eq(&root_coto.uuid),
                    ..
                })]
            )
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: until cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let (coto4, _) = ds.post_coto(&CotoInput::new("coto4"), &root.uuid, &opr)?;
    connect(&ItoInput::new(cotonoma1.coto_id, coto4.uuid))?;

    // until_cotonoma = true
    let expected_dot = indoc! {r#"
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
    "#};
    assert_graph(ds.graph(root_coto.clone(), true)?, expected_dot);
    assert_graph(ds.graph_by_cte(root_coto.clone(), true)?, expected_dot);

    // until_cotonoma = false
    let expected_dot = indoc! {r#"
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
    "#};
    assert_graph(ds.graph(root_coto.clone(), false)?, expected_dot);
    assert_graph(ds.graph_by_cte(root_coto.clone(), false)?, expected_dot);

    Ok(())
}

fn assert_graph(graph: Graph, expected_dot: &str) {
    assert_that!(
        Dot::new(&graph.into_petgraph(true)).to_string(),
        eq(expected_dot)
    );
}
