--
-- A changelog is a series of changes in a Cotoami database recorded 
-- for state machine replication.
--
-- A node (database) could incorporate a same node more than once via 
-- different parents, therefore it could receive a same changelog entry 
-- more than once. The pair of `origin_node_id` and `origin_serial_number` with 
-- a unique constraint is a way to avoid duplicate entries inserted in changelog.
--
CREATE TABLE changelog (
  -- Serial number of a changelog entry based on SQLite ROWID
  --
  -- ROWID will be filled automatically with an unused integer, 
  -- usually one more than the largest ROWID currently in use.
  --
  -- When replicating a database to another node, that node must ensure to 
  -- apply the changelog entries in the serial number order (state machine replication).
  --
  -- If it is possible for an entry with the largest ROWID to be deleted, 
  -- we should add an `AUTOINCREMENT` keyword to prevent the reuse of ROWIDs 
  -- from previously deleted rows. - https://www.sqlite.org/autoinc.html
  serial_number INTEGER NOT NULL PRIMARY KEY,
  
  -- UUID of the node in which this change has been originally created.
  origin_node_id TEXT NOT NULL,

  -- Serial number among changes created in the origin node.
  origin_serial_number INTEGER NOT NULL,

  -- Number to distinguish between different change types (Change::type_number()).
  type_number INTEGER NOT NULL,

  -- Change (cotoami_db::models::changelog::Change) value in MessagePack form.
  change BLOB NOT NULL,

  -- Error occurred during importing this change to the local node.
  import_error TEXT,

  -- Registration date in this database.
  inserted_at DATETIME NOT NULL, -- UTC

  UNIQUE(origin_node_id, origin_serial_number)
);
