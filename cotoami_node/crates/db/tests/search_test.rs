use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn search_cotos() -> Result<()> {
    // setup
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.local_node_root()?.unwrap();

    // when
    let (coto1, _) = ds.post_coto(&CotoInput::new("Hello, world!"), &root.uuid, &opr)?;
    let (coto2, _) = ds.post_coto(
        &CotoInput::new("It's a small world.").summary("summary"),
        &root.uuid,
        &opr,
    )?;
    let (coto3, _) = ds.post_coto(
        &CotoInput::new("柿くへば鐘が鳴るなり法隆寺"),
        &root.uuid,
        &opr,
    )?;
    let (coto4, _) = ds.post_coto(&CotoInput::new("旅行(行きたい)"), &root.uuid, &opr)?;

    // then
    assert_search(&mut ds, "hello", vec![&coto1])?;
    assert_search(&mut ds, "world", vec![&coto1, &coto2])?;
    assert_search(&mut ds, "法隆寺", vec![&coto3])?;
    assert_search(&mut ds, "(", vec![])?; // parentheses as a query
    assert_search(&mut ds, "\"", vec![])?; // double quote as a query

    // search by CJK words shorter than trigram tokens
    assert_search(&mut ds, "鳴る", vec![&coto3])?; // two chars
    assert_search(&mut ds, "柿", vec![&coto3])?; // one char
    assert_search(&mut ds, "寺", vec![&coto3])?; // the last char
    assert_search(&mut ds, "禅", vec![])?; // no hit
    assert_search(&mut ds, "旅行", vec![&coto4])?; // a token contains parentheses

    assert_search(&mut ds, "summary", vec![&coto2])?;
    assert_search(&mut ds, "small world", vec![&coto2])?; // AND
    assert_search(&mut ds, "柿 法隆寺", vec![&coto3])?; // AND

    // when: edit a coto to change English results
    // (testing the trigger: `cotos_fts_update`)
    let diff = CotoContentDiff::default().content("No Promises to Keep");
    let (coto2, _) = ds.edit_coto(&coto2.uuid, diff, &opr)?;
    assert_search(&mut ds, "world", vec![&coto1])?;
    assert_search(&mut ds, "promise", vec![&coto2])?;

    // when: edit a coto to change CJK results
    // (testing the trigger: `cotos_fts_update`)
    let diff = CotoContentDiff::default().content("色即是空空即是色");
    let (coto3, _) = ds.edit_coto(&coto3.uuid, diff, &opr)?;
    assert_search(&mut ds, "法隆寺", vec![])?;
    assert_search(&mut ds, "色即是空", vec![&coto3])?;

    // when: delete a coto in English
    // (testing the trigger: `cotos_fts_delete`)
    let _ = ds.delete_coto(&coto1.uuid, &opr)?;
    assert_search(&mut ds, "hello", vec![])?;

    // when: delete a coto in CJK
    // (testing the trigger: `cotos_fts_delete`)
    let _ = ds.delete_coto(&coto3.uuid, &opr)?;
    assert_search(&mut ds, "色即是空", vec![])?;

    Ok(())
}

#[test]
fn search_cotos_in_scope() -> Result<()> {
    // setup
    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.local_node_root()?.unwrap();

    // when
    let ((child1, _), _) = ds.post_cotonoma(&CotonomaInput::new("Scope Child1"), &root, &opr)?;
    let ((child2, _), _) = ds.post_cotonoma(&CotonomaInput::new("Scope Child2"), &child1, &opr)?;
    let ((child3, _), _) = ds.post_cotonoma(&CotonomaInput::new("Scope Child3"), &child2, &opr)?;

    let (root_coto, _) = ds.post_coto(&CotoInput::new("scopeprobe root"), &root.uuid, &opr)?;
    let (child1_coto, _) =
        ds.post_coto(&CotoInput::new("scopeprobe child1"), &child1.uuid, &opr)?;
    let (child2_coto, _) =
        ds.post_coto(&CotoInput::new("scopeprobe child2"), &child2.uuid, &opr)?;
    let (child3_coto, _) =
        ds.post_coto(&CotoInput::new("scopeprobe child3"), &child3.uuid, &opr)?;

    // then
    assert_search_in_scope(
        &mut ds,
        "scopeprobe",
        Scope::All,
        vec![&root_coto, &child1_coto, &child2_coto, &child3_coto],
    )?;
    assert_search_in_scope(
        &mut ds,
        "scopeprobe",
        Scope::Node(node.uuid),
        vec![&root_coto, &child1_coto, &child2_coto, &child3_coto],
    )?;
    assert_search_in_scope(
        &mut ds,
        "scopeprobe",
        Scope::Cotonoma((child1.uuid, CotonomaScope::Local)),
        vec![&child1_coto],
    )?;
    assert_search_in_scope(
        &mut ds,
        "scopeprobe",
        Scope::Cotonoma((child1.uuid, CotonomaScope::Recursive)),
        vec![&child1_coto, &child2_coto, &child3_coto],
    )?;
    assert_search_in_scope(
        &mut ds,
        "scopeprobe",
        Scope::Cotonoma((child1.uuid, CotonomaScope::Depth(1))),
        vec![&child1_coto, &child2_coto],
    )?;

    Ok(())
}

fn assert_search(ds: &mut DatabaseSession<'_>, query: &str, expect: Vec<&Coto>) -> Result<()> {
    assert_that!(
        ds.search_cotos(query, Scope::All, false, 10, 0)?
            .rows
            .iter()
            .collect::<Vec<_>>(),
        eq(&expect)
    );
    Ok(())
}

fn assert_search_in_scope(
    ds: &mut DatabaseSession<'_>,
    query: &str,
    scope: Scope,
    expect: Vec<&Coto>,
) -> Result<()> {
    let rows = ds.search_cotos(query, scope, false, 100, 0)?.rows;
    let actual_ids: Vec<_> = rows.iter().map(|coto| coto.uuid).collect();
    assert_that!(actual_ids.len(), eq(expect.len()));
    for expected in expect {
        assert_that!(actual_ids.contains(&expected.uuid), eq(true));
    }
    Ok(())
}
