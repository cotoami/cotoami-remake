--
-- A node is a single Cotoami database that has connections to/from other databases(nodes).
-- `nodes` table contains all the nodes appeared in the database of this node.
--
CREATE TABLE nodes (
  -- An alias for the SQLite rowid (so-called "integer primary key")
  -- The row with rowid `1` represents this node ("self node row").
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- Universally unique node ID
  uuid TEXT NOT NULL UNIQUE,

  -- Icon image
  icon BLOB NOT NULL,

  -- Display name
  name TEXT NOT NULL,

  -- Password for owner authentication of this node
  -- This value can be set only in "self node row", 
  -- therefore, it must not be sent to other nodes.
  owner_password_hash TEXT,

  -- Version of node info
  version INTEGER DEFAULT 1 NOT NULL,

  -- Creation date of this node
  -- Should not change in any database as long as the ID is the same
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- UTC

  -- Registration date in this database
  inserted_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL -- UTC
);


--
-- A parent node is a server node to which this node is connecting.
--
CREATE TABLE parent_nodes (
  -- An alias for the SQLite rowid (so-called "integer primary key")
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- UUID of a parent node
  node_id TEXT NOT NULL UNIQUE,

  -- URL prefix to connect to this parent node 
  url_prefix TEXT NOT NULL,

  -- Date when this connection was created
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);

CREATE INDEX parent_nodes_node_id ON parent_nodes(node_id);


--
-- A child node is a client node connecting to this node.
--
CREATE TABLE child_nodes (
  -- An alias for the SQLite rowid (so-called "integer primary key")
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- UUID of a child node
  node_id TEXT NOT NULL UNIQUE,

  -- Password for authentication
  password_hash TEXT NOT NULL,

  -- Permission to edit links in this database
  -- 0 (false) and 1 (true)
  can_edit_links INTEGER DEFAULT FALSE NOT NULL,

  -- Date when this connection was created
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);

CREATE INDEX child_nodes_node_id ON child_nodes(node_id);


--
-- This table contains all the nodes imported (directly or indirectly) in this database.
--
CREATE TABLE imported_nodes (
  -- An alias for the SQLite rowid (so-called "integer primary key")
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- UUID of a node imported in this database
  node_id TEXT NOT NULL UNIQUE,

  -- Date when this node was imported
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);

CREATE INDEX imported_nodes_node_id ON imported_nodes(node_id);
