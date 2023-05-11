--
-- cotos
--
DROP TRIGGER coto_updated;

DROP INDEX cotos_node_id;
DROP INDEX cotos_posted_in_id;
DROP INDEX cotos_posted_by_id;
DROP INDEX cotos_repost_of_id;

DROP TABLE cotos;

--
-- cotonomas
--
DROP TRIGGER cotonoma_updated;

DROP INDEX cotonomas_node_id;
DROP INDEX cotonomas_coto_id;

DROP TABLE cotonomas;

--
-- links
--
DROP TRIGGER link_updated;

DROP INDEX links_node_id;
DROP INDEX links_tail_coto_id;
DROP INDEX links_head_coto_id;

DROP TABLE links;
