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
    let (coto2, _) = ds.post_coto("It's a small world.", None, &root, &opr)?;
    let (coto3, _) = ds.post_coto("柿くへば鐘が鳴るなり法隆寺", None, &root, &opr)?;
    let coto1_id = coto1.uuid;
    let coto2_id = coto2.uuid;

    // then
    assert_eq!(ds.search_cotos("hello")?, vec![coto1.clone()]);
    assert_eq!(ds.search_cotos("world")?, vec![coto1, coto2]);
    assert_eq!(ds.search_cotos("法隆寺")?, vec![coto3.clone()]);

    // when: delete a coto
    let _ = ds.delete_coto(&coto1_id, &opr)?;

    // then
    assert_eq!(ds.search_cotos("hello")?, vec![]);

    // when: edit a coto to add a "法隆寺" result
    let (coto2, _) = ds.edit_coto(&coto2_id, "法隆寺", None, &opr)?;

    // then
    assert_eq!(ds.search_cotos("法隆寺")?, vec![coto2, coto3.clone()]);

    // when: edit a coto to remove a "法隆寺" result
    let (_coto2, _) = ds.edit_coto(&coto2_id, "斑鳩寺", None, &opr)?;

    // then
    assert_eq!(ds.search_cotos("法隆寺")?, vec![coto3]);

    Ok(())
}
