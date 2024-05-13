use anyhow::Result;
use cotoami_db::prelude::{Coto, DatabaseSession};

pub mod common;

#[test]
fn search_cotos() -> Result<()> {
    // setup
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.root_cotonoma()?.unwrap();

    // when
    let (coto1, _) = ds.post_coto("Hello, world!", None, &root, &opr)?;
    let (coto2, _) = ds.post_coto("It's a small world.", Some("summary"), &root, &opr)?;
    let (coto3, _) = ds.post_coto("柿くへば鐘が鳴るなり法隆寺", None, &root, &opr)?;
    let (coto4, _) = ds.post_coto("旅行(行きたい)", None, &root, &opr)?;

    // then
    assert_search(&mut ds, "hello", vec![&coto1])?;
    assert_search(&mut ds, "world", vec![&coto1, &coto2])?;
    assert_search(&mut ds, "法隆寺", vec![&coto3])?;

    // search by CJK words shorter than trigram tokens
    assert_search(&mut ds, "鳴る", vec![&coto3])?; // two chars
    assert_search(&mut ds, "柿", vec![&coto3])?; // one char
    assert_search(&mut ds, "寺", vec![&coto3])?; // the last char
    assert_search(&mut ds, "禅", vec![])?; // no hit
    assert_search(&mut ds, "旅行", vec![&coto4])?; // a token contains parentheses

    assert_search(&mut ds, "summary", vec![&coto2])?;
    assert_search(&mut ds, "small OR world", vec![&coto2, &coto1])?;
    assert_search(&mut ds, "small AND world", vec![&coto2])?;

    // when: edit a coto to change English results
    // (testing the trigger: `cotos_fts_update`)
    let (coto2, _) = ds.edit_coto(&coto2.uuid, "No Promises to Keep", None, &opr)?;
    assert_search(&mut ds, "world", vec![&coto1])?;
    assert_search(&mut ds, "promise", vec![&coto2])?;

    // when: edit a coto to change CJK results
    // (testing the trigger: `cotos_fts_update`)
    let (coto3, _) = ds.edit_coto(&coto3.uuid, "色即是空空即是色", None, &opr)?;
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

fn assert_search(ds: &mut DatabaseSession<'_>, query: &str, expect: Vec<&Coto>) -> Result<()> {
    assert_eq!(
        ds.search_cotos(query, None, None, 10, 0)?
            .rows
            .iter()
            .collect::<Vec<_>>(),
        expect
    );
    Ok(())
}
