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

    // assert: recent cotos in "Object-Oriented"
    assert_that!(
        timeline(&mut ds, Scope::cotonoma_recursive(cotonoma1.uuid))?,
        elements_are![eq("POJO"), eq("cotonoma:Java"), eq("polymorphism"),]
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

    // assert: recent cotos in "Object-Oriented"
    assert_that!(
        timeline(&mut ds, Scope::cotonoma_recursive(cotonoma1.uuid))?,
        elements_are![
            eq(&format!("repost:{}", cotonoma_coto4.uuid)),
            eq("sbt"),
            eq("POJO"),
            eq("cotonoma:Java"),
            eq("polymorphism"),
        ]
    );
    Ok(())
}

/// Returns recent cotos as [Vec<String>] in the given scope.
fn timeline(ds: &mut DatabaseSession<'_>, scope: Scope) -> Result<Vec<String>> {
    Ok(ds
        .recent_cotos(scope, false, 100, 0)?
        .rows
        .into_iter()
        .map(|coto| match coto {
            Coto {
                repost_of_id: Some(coto_id),
                ..
            } => {
                format!("repost:{coto_id}")
            }
            Coto {
                is_cotonoma: true,
                summary: Some(name),
                ..
            } => format!("cotonoma:{name}"),
            Coto {
                content: Some(content),
                ..
            } => content,
            _ => "".into(),
        })
        .collect())
}
