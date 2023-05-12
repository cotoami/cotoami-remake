--
-- A node is a single cotoami database that has connections to/from other databases(nodes).
-- `nodes` table contains all the nodes appeared in the database of this node.
--
CREATE TABLE nodes (
  -- An alias for the SQLite rowid (so-called "integer primary key")
  -- The row with rowid `1` represents this node.
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- Universally unique node ID
  uuid TEXT NOT NULL UNIQUE,

  -- Display name
  name TEXT NOT NULL,

  -- Icon image
  icon BLOB NOT NULL,

  -- For nodes connecting to this node
  password_hash TEXT,

  -- Permission to edit links in the database of this node.
  -- 0 (false) and 1 (true)
  can_edit_links INTEGER DEFAULT FALSE NOT NULL,

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

  -- UUID of a parent node of this node
  node_id TEXT NOT NULL UNIQUE,

  -- URL prefix of a parent node 
  url_prefix TEXT,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);

CREATE INDEX parent_nodes_node_id ON parent_nodes(node_id);
