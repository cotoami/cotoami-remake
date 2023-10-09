--
-- A node is a single Cotoami database that has connections to/from other databases(nodes).
-- 
-- `nodes` table should contain all nodes that appears in the attributes of entities
--- (cotos/cotonomas/links) in this database.
--
CREATE TABLE nodes (
  -- Universally unique node ID
  -- This column is used as a primary key in the Diesel models.
  uuid TEXT NOT NULL UNIQUE,

  -- An alias for the SQLite rowid (so-called "integer primary key")
  -- This serial number is used to return nodes in registration order.
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- Icon image
  icon BLOB NOT NULL,

  -- Display name
  name TEXT NOT NULL,

  -- Version of node info
  version INTEGER DEFAULT 1 NOT NULL,

  -- Creation date of this node
  -- Should not change in any database as long as the ID is the same
  created_at DATETIME NOT NULL -- UTC
);


--
-- A database instance self-references itself as a "local node".
-- This table can have only one row that has a node ID pointing to the local node 
-- and its extended attributes.
--
CREATE TABLE local_node (
  -- UUID of a local node
  -- This column is used as a primary key in the Diesel models.
  node_id TEXT NOT NULL UNIQUE,

  -- An alias for the SQLite rowid (so-called "integer primary key")
  -- With the CHECK constraint, it enforces that you can't insert more than one row.
  rowid INTEGER NOT NULL PRIMARY KEY CHECK(rowid = 1),

  -- Password for owner authentication of this local node
  owner_password_hash TEXT,

  -- Node owner's session token
  owner_session_token TEXT,

  -- Expiration date of node owner's session
  owner_session_expires_at DATETIME, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);


--
-- A parent node is a server node to which this node is connecting.
-- A row of this table has a node ID pointing to a parent node and its extended attributes.
--
CREATE TABLE parent_nodes (
  -- UUID of a parent node
  node_id TEXT NOT NULL PRIMARY KEY,

  -- URL prefix to connect to this parent node 
  url_prefix TEXT NOT NULL,

  -- Date when this connection was created
  created_at DATETIME NOT NULL, -- UTC

  -- Saved password to connect to this parent node
  encrypted_password BLOB,

  -- Number of changes received from this parent node
  -- This number corresponds to `changelog.serial_number` in the parent node.
  changes_received INTEGER DEFAULT 0 NOT NULL,

  -- Date when received the last change from this parent node
  last_change_received_at DATETIME, -- UTC

  -- Local node won't connect to this parent node if the value is TRUE.
  disabled INTEGER DEFAULT FALSE NOT NULL,

  -- TRUE if the local node has been forked from this parent node.
  -- A forked child can never connect to or accept changes 
  -- (directly or indirectly) from the parent again.
  forked INTEGER DEFAULT FALSE NOT NULL,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;


--
-- A child node is a client node connecting to this node.
-- A row of this table has a node ID pointing to a child node and its extended attributes.
--
CREATE TABLE child_nodes (
  -- UUID of a child node
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Password for authentication
  password_hash TEXT NOT NULL,

  -- Login session token
  session_token TEXT UNIQUE,

  -- Expiration date of login session
  session_expires_at DATETIME, -- UTC

  -- TRUE if this node has the same permission as the owner
  -- 0 (false) and 1 (true)
  as_owner INTEGER DEFAULT FALSE NOT NULL,

  -- Permission to edit links in this database
  -- 0 (false) and 1 (true)
  can_edit_links INTEGER DEFAULT FALSE NOT NULL,

  -- Date when this connection was created
  created_at DATETIME NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;
