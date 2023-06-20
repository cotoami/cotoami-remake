--
-- A node is a single Cotoami database that has connections to/from other databases(nodes).
-- `nodes` table should contain all nodes appeared in this database.
--
CREATE TABLE nodes (
  -- Universally unique node ID
  -- This column is used as a primary key in the Diesel models.
  uuid TEXT NOT NULL UNIQUE,

  -- An alias for the SQLite rowid (so-called "integer primary key")
  -- The rowid `1` denotes the "local node row".
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- Icon image
  icon BLOB NOT NULL,

  -- Display name
  name TEXT NOT NULL,

  -- Password for owner authentication of this node
  -- This value can be set only in "local node row", therefore, 
  -- it must not be sent to other nodes.
  owner_password_hash TEXT,

  -- Version of node info
  version INTEGER DEFAULT 1 NOT NULL,

  -- Creation date of this node
  -- Should not change in any database as long as the ID is the same
  created_at DATETIME NOT NULL, -- UTC

  -- Registration date in this database
  inserted_at DATETIME NOT NULL -- UTC
);


--
-- A parent node is a server node to which this node is connecting.
--
CREATE TABLE parent_nodes (
  -- UUID of a parent node
  node_id TEXT NOT NULL PRIMARY KEY,

  -- URL prefix to connect to this parent node 
  url_prefix TEXT NOT NULL,

  -- Date when this connection was created
  created_at DATETIME NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE INDEX parent_nodes_node_id ON parent_nodes(node_id);


--
-- A child node is a client node connecting to this node.
--
CREATE TABLE child_nodes (
  -- UUID of a child node
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Password for authentication
  password_hash TEXT NOT NULL,

  -- Permission to edit links in this database
  -- 0 (false) and 1 (true)
  can_edit_links INTEGER DEFAULT FALSE NOT NULL,

  -- Date when this connection was created
  created_at DATETIME NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE INDEX child_nodes_node_id ON child_nodes(node_id);


--
-- This table contains all the nodes whose databases have been incorporated 
-- (directly or indirectly) in the coto graph.
--
CREATE TABLE incorporated_nodes (
  -- UUID of a node incorporated in this database
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Date when this node was incorporated
  created_at DATETIME NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;

CREATE INDEX incorporated_nodes_node_id ON incorporated_nodes(node_id);
