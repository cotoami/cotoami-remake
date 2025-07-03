--
-- A node is a single database instance that can have connections to/from other nodes.
--
-- Rows in this table will be shared with other nodes via changelogs, or during 
-- logging into a server node.
-- 
-- `nodes` table should contain all nodes that appears in the attributes of entities
-- (cotos/cotonomas/itos) in this database.
--
CREATE TABLE nodes (
  -- Universally unique node ID.
  -- This column is used as a primary key in the Diesel models.
  uuid TEXT NOT NULL UNIQUE,

  -- An alias for the SQLite rowid (so-called "integer primary key").
  -- This serial number is used to return nodes in registration order.
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- Bytes of a PNG image for the Node icon.
  icon BLOB NOT NULL,

  -- Display name.
  name TEXT NOT NULL,

  -- Version of node info.
  version INTEGER DEFAULT 1 NOT NULL,

  -- Creation date of this node.
  -- Should not change in any database as long as the ID is the same.
  created_at DATETIME NOT NULL -- UTC
);


--
-- Node roles
--
-- Each of the following tables represents a role of a node, appending additional 
-- attributes to rows in `nodes`.
--
-- Those roles are used internally inside a node and can contain secret data, 
-- so won't be shared with other nodes unlike data in the `nodes` table.
--
-- As for the `WITHOUT ROWID` tables with a non-integer primary key, they can use 
-- about half the amount of disk space and can operate nearly twice as fast compared 
-- to default ROWID tables.
-- https://www.sqlite.org/withoutrowid.html
--

--
-- A database instance self-references itself as a "local node".
-- This table can have only one row that represents the local node.
--
CREATE TABLE local_node (
  -- UUID of a local node.
  -- This column is used as a primary key in the Diesel models.
  node_id TEXT NOT NULL UNIQUE,

  -- An alias for the SQLite rowid (so-called "integer primary key").
  -- With the CHECK constraint, it enforces that you can't insert more than one row.
  rowid INTEGER NOT NULL PRIMARY KEY CHECK(rowid = 1),

  -- Password for owner authentication of this local node.
  owner_password_hash TEXT,

  -- Node owner's session token.
  owner_session_token TEXT,

  -- Expiration date of node owner's session.
  owner_session_expires_at DATETIME, -- UTC

  -- The maximum length of the longer side of images after they are resized (in pixels).
  -- NULL means no resizing will be applied to incoming coto images.
  image_max_size INTEGER,

  -- TRUE if this node allows anonymous clients to read the cotos and itos.
  anonymous_read_enabled INTEGER DEFAULT FALSE NOT NULL,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
);


--
-- Server nodes to which the local node is connecting.
--
CREATE TABLE server_nodes (
  -- UUID of a server node.
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Date when this connection was created.
  created_at DATETIME NOT NULL, -- UTC

  -- URL prefix to connect to this server node.
  url_prefix TEXT NOT NULL,

  -- Saved password to connect to this server node.
  encrypted_password BLOB,

  -- Local node won't connect to this node if the value is TRUE.
  disabled INTEGER DEFAULT FALSE NOT NULL,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;


--
-- Accounts for client nodes to allow them to connect to the local 
-- node with their password.
--
CREATE TABLE client_nodes (
  -- UUID of a client node.
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Date when this account was created.
  created_at DATETIME NOT NULL, -- UTC

  -- Password for authentication.
  password_hash TEXT NOT NULL,

  -- Login session token.
  session_token TEXT UNIQUE,

  -- Expiration date of login session.
  session_expires_at DATETIME, -- UTC

  -- Local node won't allow this node to connect to it if the value is TRUE.
  disabled INTEGER DEFAULT FALSE NOT NULL,

  -- Timestamp when the last session was created.
  last_session_created_at DATETIME, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;


--
-- A parent node is a node whose database is incorporated into the local node.
--
CREATE TABLE parent_nodes (
  -- UUID of a parent node.
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Date when this parent was incorporated into the local node.
  created_at DATETIME NOT NULL, -- UTC

  -- Number of changes received from this parent node.
  -- This number corresponds to `changelog.serial_number` in the parent node.
  changes_received INTEGER DEFAULT 0 NOT NULL,

  -- Date when received the last change from this parent node.
  last_change_received_at DATETIME, -- UTC

  -- TRUE if the local node has been forked from this parent node.
  -- A forked child can never connect to or accept changes 
  -- (directly or indirectly) from the parent again.
  forked INTEGER DEFAULT FALSE NOT NULL,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;


--
-- A child node is a node incorporating the database of the local node.
--
CREATE TABLE child_nodes (
  -- UUID of a child node.
  node_id TEXT NOT NULL PRIMARY KEY,

  -- Date when this child incorporated the local node.
  created_at DATETIME NOT NULL, -- UTC

  -- TRUE if this node has the same permission as the owner.
  -- 0 (false) and 1 (true).
  as_owner INTEGER DEFAULT FALSE NOT NULL,

  -- Permission to edit itos in this database.
  -- 0 (false) and 1 (true).
  can_edit_itos INTEGER DEFAULT FALSE NOT NULL,

  -- Permission to post cotonomas in this database.
  -- 0 (false) and 1 (true).
  can_post_cotonomas INTEGER DEFAULT FALSE NOT NULL,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT
) WITHOUT ROWID;
