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
DROP TRIGGER cotonomas_cotos_sync;

DROP INDEX cotonomas_node_id;
DROP INDEX cotonomas_coto_id;
DROP INDEX cotonomas_name;

DROP TABLE cotonomas;

--
-- itos
--
DROP INDEX itos_node_id;
DROP INDEX itos_created_by_id;
DROP INDEX itos_source_coto_id;
DROP INDEX itos_target_coto_id;

DROP TABLE itos;
