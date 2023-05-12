--
-- nodes
--
DROP TABLE nodes;

--
-- parent_nodes
--
DROP INDEX parent_nodes_node_id;
DROP TABLE parent_nodes;

--
-- child_nodes
--
DROP INDEX child_nodes_node_id;
DROP TABLE child_nodes;

--
-- imported_nodes
--
DROP INDEX imported_nodes_node_id;
DROP TABLE imported_nodes;
