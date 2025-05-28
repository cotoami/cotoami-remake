-- Timestamp of the latest coto the user has read in the local_node.
-- All cotos created by others after this timestamp are considered unread.
ALTER TABLE local_node ADD COLUMN last_read_at DATETIME; -- UTC

-- Timestamp of the latest coto the user has read in this parent.
-- All cotos created by others after this timestamp are considered unread.
ALTER TABLE parent_nodes ADD COLUMN last_read_at DATETIME; -- UTC
