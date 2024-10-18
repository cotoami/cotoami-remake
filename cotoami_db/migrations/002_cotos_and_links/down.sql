--
-- cotos
--
DROP TRIGGER cotos_reposts_sync;

DROP INDEX cotos_node_id;
DROP INDEX cotos_posted_in_id;
DROP INDEX cotos_posted_by_id;
DROP INDEX cotos_lng_lat;
DROP INDEX cotos_datetime_start;
DROP INDEX cotos_datetime_end;
DROP INDEX cotos_repost_of_id;

DROP TABLE cotos;

--
-- cotonomas
--
DROP INDEX cotonomas_node_id;
DROP INDEX cotonomas_coto_id;

DROP TABLE cotonomas;

--
-- links
--
DROP INDEX links_node_id;
DROP INDEX links_created_by_id;
DROP INDEX links_source_coto_id;
DROP INDEX links_target_coto_id;

DROP TABLE links;
