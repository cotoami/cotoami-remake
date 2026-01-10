use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn cotonoma_tree() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_root_dir, db, _node) = common::setup_db("Programming languages")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // Case1: sub cotonomas
    /////////////////////////////////////////////////////////////////////////////

    let ((cotonoma1, _), _) =
        ds.post_cotonoma(&CotonomaInput::new("Object-Oriented"), &root_cotonoma, &opr)?;
    let ((cotonoma2, _), _) =
        ds.post_cotonoma(&CotonomaInput::new("Functional"), &root_cotonoma, &opr)?;

    let _ = ds.post_coto(&CotoInput::new("polymorphism"), &cotonoma1.uuid, &opr)?;
    let _ = ds.post_coto(&CotoInput::new("monad"), &cotonoma2.uuid, &opr)?;

    // assert: sub_cotonomas
    let subs = ds.sub_cotonomas(&root_cotonoma.uuid, 5, 0)?;
    assert_that!(
        subs.rows,
        elements_are![
            pat!(Cotonoma {
                name: eq("Functional"),
                ..
            }),
            pat!(Cotonoma {
                name: eq("Object-Oriented"),
                ..
            }),
        ],
    );

    // assert: sub_cotonoma_ids_recursive
    assert_that!(
        ds.sub_cotonoma_ids_recursive(&root_cotonoma.uuid, None)?,
        unordered_elements_are![eq(&cotonoma1.uuid), eq(&cotonoma2.uuid)]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Case2: sub-sub cotonomas
    /////////////////////////////////////////////////////////////////////////////

    let ((cotonoma3, _), _) = ds.post_cotonoma(&CotonomaInput::new("Java"), &cotonoma1, &opr)?;
    let ((cotonoma4, cotonoma_coto4), _) =
        ds.post_cotonoma(&CotonomaInput::new("Scala"), &cotonoma2, &opr)?;

    let _ = ds.post_coto(&CotoInput::new("POJO"), &cotonoma3.uuid, &opr)?;
    let _ = ds.post_coto(&CotoInput::new("sbt"), &cotonoma4.uuid, &opr)?;

    // assert: sub_cotonoma_ids_recursive
    assert_that!(
        ds.sub_cotonoma_ids_recursive(&root_cotonoma.uuid, None)?,
        unordered_elements_are![
            eq(&cotonoma1.uuid),
            eq(&cotonoma2.uuid),
            eq(&cotonoma3.uuid),
            eq(&cotonoma4.uuid)
        ]
    );
    assert_that!(
        ds.sub_cotonoma_ids_recursive(&root_cotonoma.uuid, Some(1))?,
        unordered_elements_are![eq(&cotonoma1.uuid), eq(&cotonoma2.uuid),]
    );

    // assert: recent_cotos in "Object-Oriented"
    assert_that!(
        ds.recent_cotos(
            Scope::Cotonoma((cotonoma1.uuid, CotonomaScope::Recursive)),
            false,
            5,
            0
        )?
        .rows,
        elements_are![
            pat!(Coto {
                content: some(eq("POJO")),
                ..
            }),
            pat!(Coto {
                summary: some(eq("Java")),
                is_cotonoma: eq(&true),
                ..
            }),
            pat!(Coto {
                content: some(eq("polymorphism")),
                ..
            }),
        ]
    );

    /////////////////////////////////////////////////////////////////////////////
    // Case2: repost
    /////////////////////////////////////////////////////////////////////////////

    let _ = ds.repost(&cotonoma_coto4.uuid, &cotonoma1, &opr)?;

    // assert: sub_cotonomas should contain the reposted cotonoma
    let subs = ds.sub_cotonomas(&cotonoma1.uuid, 5, 0)?;
    assert_that!(
        subs.rows,
        elements_are![
            pat!(Cotonoma {
                name: eq("Scala"),
                ..
            }),
            pat!(Cotonoma {
                name: eq("Java"),
                ..
            }),
        ]
    );

    // assert: sub_cotonoma_ids_recursive
    assert_that!(
        ds.sub_cotonoma_ids_recursive(&root_cotonoma.uuid, None)?,
        unordered_elements_are![
            eq(&cotonoma1.uuid),
            eq(&cotonoma2.uuid),
            eq(&cotonoma3.uuid),
            eq(&cotonoma4.uuid)
        ]
    );
    assert_that!(
        ds.sub_cotonoma_ids_recursive(&cotonoma1.uuid, None)?,
        unordered_elements_are![eq(&cotonoma3.uuid), eq(&cotonoma4.uuid)]
    );

    // assert: recent_cotos in "Object-Oriented"
    assert_that!(
        ds.recent_cotos(
            Scope::Cotonoma((cotonoma1.uuid, CotonomaScope::Recursive)),
            false,
            5,
            0
        )?
        .rows,
        elements_are![
            pat!(Coto {
                repost_of_id: some(eq(&cotonoma_coto4.uuid)),
                ..
            }),
            pat!(Coto {
                content: some(eq("sbt")),
                ..
            }),
            pat!(Coto {
                content: some(eq("POJO")),
                ..
            }),
            pat!(Coto {
                summary: some(eq("Java")),
                is_cotonoma: eq(&true),
                ..
            }),
            pat!(Coto {
                content: some(eq("polymorphism")),
                ..
            }),
        ]
    );
    Ok(())
}
