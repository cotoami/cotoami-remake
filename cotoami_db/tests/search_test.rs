use anyhow::Result;

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

    // then
    assert_eq!(
        ds.search_cotos("hello")?.iter().collect::<Vec<_>>(),
        vec![&coto1]
    );
    assert_eq!(
        ds.search_cotos("world")?.iter().collect::<Vec<_>>(),
        vec![&coto1, &coto2]
    );
    assert_eq!(
        ds.search_cotos("法隆寺")?.iter().collect::<Vec<_>>(),
        vec![&coto3]
    );
    assert_eq!(
        ds.search_cotos("summary")?.iter().collect::<Vec<_>>(),
        vec![&coto2]
    );
    assert_eq!(
        ds.search_cotos("small OR world")?
            .iter()
            .collect::<Vec<_>>(),
        vec![&coto2, &coto1]
    );
    assert_eq!(
        ds.search_cotos("small AND world")?
            .iter()
            .collect::<Vec<_>>(),
        vec![&coto2]
    );

    // when: edit a coto to change English results
    // (testing the trigger: `cotos_fts_update`)
    let (coto2, _) = ds.edit_coto(&coto2.uuid, "No Promises to Keep", None, &opr)?;
    assert_eq!(
        ds.search_cotos("world")?.iter().collect::<Vec<_>>(),
        vec![&coto1]
    );
    assert_eq!(
        ds.search_cotos("promise")?.iter().collect::<Vec<_>>(),
        vec![&coto2]
    );

    // when: edit a coto to change CJK results
    // (testing the trigger: `cotos_fts_update`)
    let (coto3, _) = ds.edit_coto(&coto3.uuid, "色即是空空即是色", None, &opr)?;
    assert_eq!(ds.search_cotos("法隆寺")?, vec![]);
    assert_eq!(
        ds.search_cotos("色即是空")?.iter().collect::<Vec<_>>(),
        vec![&coto3]
    );

    // when: delete a coto in English
    // (testing the trigger: `cotos_fts_delete`)
    let _ = ds.delete_coto(&coto1.uuid, &opr)?;
    assert_eq!(ds.search_cotos("hello")?, vec![]);

    // when: delete a coto in CJK
    // (testing the trigger: `cotos_fts_delete`)
    let _ = ds.delete_coto(&coto3.uuid, &opr)?;
    assert_eq!(ds.search_cotos("色即是空")?, vec![]);

    Ok(())
}
