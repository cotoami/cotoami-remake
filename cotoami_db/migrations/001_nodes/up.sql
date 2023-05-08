-- A node is a single cotoami database that has connections to/from other databases(nodes).
-- `nodes` table contains all the nodes appeared in the database of this node.
CREATE TABLE nodes (
  -- An alias for the SQLite rowid (so-called "integer primary key")
  -- The row with rowid `1` represents this node.
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- Node ID
  uuid TEXT NOT NULL UNIQUE,

  -- Display name
  name TEXT,

  -- For nodes being connected from this node
  url_prefix TEXT,

  -- For nodes connecting to this node
  password TEXT,

  -- Permission to edit links in the database of this node.
  -- 0 (false) and 1 (true)
  can_edit_links INTEGER DEFAULT FALSE NOT NULL,

  created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL -- UTC
);
