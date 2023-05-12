-- UUID of the root cotonoma of this node
ALTER TABLE nodes 
ADD COLUMN root_cotonoma_id TEXT REFERENCES cotonomas(uuid) ON DELETE SET NULL;

CREATE INDEX nodes_root_cotonoma_id ON nodes(root_cotonoma_id);
