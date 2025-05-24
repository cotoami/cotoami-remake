-- Timestamp of the latest coto the user has read from this parent.
-- All cotos created by others after this timestamp are considered unread.
ALTER TABLE parent_nodes ADD COLUMN last_read_at DATETIME; -- UTC
