use anyhow::Result;
use cotoami_db::Coto;

pub mod common;

#[test]
fn pagination() -> Result<()> {
    // setup
    let (_root_dir, db, node) = common::setup_db()?;
    let mut session = db.create_session()?;
    let cotonoma_id = node.root_cotonoma_id.unwrap();

    // when
    let paginated = session.recent_cotos(None, Some(&cotonoma_id), 2, 0)?;

    // then
    assert_eq!(paginated.rows.len(), 0);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 0);
    assert_eq!(paginated.total_pages(), 0);

    // when
    session.post_coto(&cotonoma_id, None, "1", None)?;
    let paginated = session.recent_cotos(None, Some(&cotonoma_id), 2, 0)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["1"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 1);
    assert_eq!(paginated.total_pages(), 1);

    // when
    session.post_coto(&cotonoma_id, None, "2", None)?;
    let paginated = session.recent_cotos(None, Some(&cotonoma_id), 2, 0)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["2", "1"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 2);
    assert_eq!(paginated.total_pages(), 1);

    // when
    session.post_coto(&cotonoma_id, None, "3", None)?;
    let paginated = session.recent_cotos(None, Some(&cotonoma_id), 2, 0)?;

    // then
    assert_eq!(into_content_vec(&paginated.rows), vec!["3", "2"]);
    assert_eq!(paginated.page_size, 2);
    assert_eq!(paginated.page_index, 0);
    assert_eq!(paginated.total_rows, 3);
    assert_eq!(paginated.total_pages(), 2);

    // when
    let paginated = session.recent_cotos(None, Some(&cotonoma_id), 2, 1)?;

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
