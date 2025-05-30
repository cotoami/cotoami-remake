-- Last time the local node was marked as read.
-- All cotos created by others after this timestamp are considered unread.
ALTER TABLE local_node ADD COLUMN last_read_at DATETIME; -- UTC

-- Last time the parent node was marked as read.
-- All cotos created by others after this timestamp are considered unread.
ALTER TABLE parent_nodes ADD COLUMN last_read_at DATETIME; -- UTC
