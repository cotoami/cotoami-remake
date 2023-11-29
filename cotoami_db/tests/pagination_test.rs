use anyhow::Result;
use cotoami_db::prelude::*;

pub mod common;

#[test]
fn pagination() -> Result<()> {
    // setup
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let operator = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.root_cotonoma()?.unwrap();
    // when
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_eq!(paginated.rows.len(), 0);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 0);
    assert_eq!(paginated.total_pages(), 0);

    // when
    let _ = ds.post_coto("1", None, &root_cotonoma, &operator)?;
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["1"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 1);
    assert_eq!(paginated.total_pages(), 1);

    // when
    let _ = ds.post_coto("2", None, &root_cotonoma, &operator)?;
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["2", "1"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 2);
    assert_eq!(paginated.total_pages(), 1);

    // when
    let _ = ds.post_coto("3", None, &root_cotonoma, &operator)?;
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["3", "2"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 3);
    assert_eq!(paginated.total_pages(), 2);

    // when
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 1)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["1"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 1);
    assert_eq!(paginated.total_rows, 3);
    assert_eq!(paginated.total_pages(), 2);

    Ok(())
}

fn into_content_vec<'a>(cotos: &'a Vec<Coto>) -> Vec<&'a String> {
    cotos
        .iter()
        .map(|coto| coto.content.as_ref().unwrap())
        .collect::<Vec<_>>()
}
