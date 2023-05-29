--
-- A changelog is a series of changes in a Cotoami database recorded 
-- for state machine replication.
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
  
  -- UUID of the parent node from which this change came
  -- NULL if it is a local change
  parent_node_id TEXT,

  -- Original serial number in the parent node
  -- NULL if it is a local change
  parent_serial_number INTEGER,

  -- Change enum value in JSON-serialized form
  change TEXT NOT NULL,

  -- Registration date in this database
  inserted_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- UTC

  FOREIGN KEY(parent_node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);

-- https://sqlite.org/partialindex.html
CREATE UNIQUE INDEX changelog_parent
ON changelog(parent_node_id, parent_serial_number) 
WHERE 
  parent_node_id IS NOT NULL AND 
  parent_serial_number IS NOT NULL;

CREATE INDEX changelog_parent_node_id ON changelog(parent_node_id);
