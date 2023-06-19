-- UUID of the root cotonoma of this node
-- It is possible that this cotonoma is not in a database when
-- the node has been imported without its database.
ALTER TABLE nodes ADD COLUMN root_cotonoma_id TEXT;
