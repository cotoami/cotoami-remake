CREATE TABLE changelog (
  -- Serial number of a changelog entry based on SQLite ROWID
  --
  -- ROWID will be filled automatically with an unused integer, 
  -- usually one more than the largest ROWID currently in use.
  --
  -- If it is possible for an entry with the largest ROWID to be deleted, 
  -- we should add an `AUTOINCREMENT` keyword to prevent the reuse of ROWIDs 
  -- from previously deleted rows. - https://www.sqlite.org/autoinc.html
  --
  -- When replicating a database in another node, that node must ensure to 
  -- apply the changelog entries in the serial number order (state machine replication).
  serial_number INTEGER NOT NULL PRIMARY KEY,
  
  -- Universally unique changelog ID
  uuid TEXT NOT NULL UNIQUE,

  -- a remote node ID to which this change belongs
  -- NULL if it is a local change
  remote_node_id TEXT,
  remote_serial_number INTEGER,

  -- Change enum value in JSON-serialized form
  change TEXT NOT NULL,

  -- Registration date in this database
  inserted_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- UTC

  FOREIGN KEY(remote_node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);

-- https://sqlite.org/partialindex.html
CREATE UNIQUE INDEX changelog_remote 
ON changelog(remote_node_id, remote_serial_number) 
WHERE 
  remote_node_id IS NOT NULL AND 
  remote_serial_number IS NOT NULL;

CREATE INDEX changelog_remote_node_id ON changelog(remote_node_id);
