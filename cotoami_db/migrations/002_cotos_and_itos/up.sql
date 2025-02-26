--
-- A coto is a unit of data in a cotoami database.
--
CREATE TABLE cotos (
  -- Universally unique coto ID.
  -- This column is used as a primary key in the Diesel models.
  uuid TEXT NOT NULL UNIQUE,

  -- An alias for the SQLite rowid (so-called "integer primary key").
  -- This serial number is used to return cotos in registration order.
  rowid INTEGER NOT NULL PRIMARY KEY,

  -- UUID of the node in which this coto was created.
  node_id TEXT NOT NULL,

  -- UUID of the cotonoma in which this coto was posted,
  -- or NULL if it is the root cotonoma.
  posted_in_id TEXT,

  -- UUID of the node whose owner has posted this coto.
  posted_by_id TEXT NOT NULL,

  -- Text content of this coto.
  -- NULL if this is a repost.
  content TEXT,

  -- Optional summary of the text content for compact display.
  summary TEXT,

  -- Bytes of optional media content.
  media_content BLOB,

  -- MIME type of the media content.
  media_type TEXT,

  -- TRUE if this coto is a cotonoma.
  -- 0 (false) and 1 (true)
  --
  -- If this is a repost, this value will sync with that of the original coto 
  -- and naturally, even if the value is true, there is no row of `cotonomas` 
  -- corresponding to this coto.
  is_cotonoma INTEGER DEFAULT FALSE NOT NULL,

  -- Geolocation
  longitude REAL,
  latitude REAL,

  -- DateTime range
  datetime_start DATETIME, -- UTC
  datetime_end DATETIME,   -- UTC

  -- UUID of the original coto of this repost,
  -- or NULL if it is not a repost.
  repost_of_id TEXT,

  -- Comma-separated UUIDs of the cotonomas in which this coto was reposted.
  reposted_in_ids TEXT,

  created_at DATETIME NOT NULL, -- UTC
  updated_at DATETIME NOT NULL, -- UTC

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(posted_in_id) REFERENCES cotonomas(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(posted_by_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(repost_of_id) REFERENCES cotos(uuid) ON DELETE CASCADE
);

CREATE INDEX cotos_node_id ON cotos(node_id);
CREATE INDEX cotos_posted_in_id ON cotos(posted_in_id);
CREATE INDEX cotos_posted_by_id ON cotos(posted_by_id);
CREATE INDEX cotos_lng_lat ON cotos(longitude, latitude);
CREATE INDEX cotos_datetime_start ON cotos(datetime_start);
CREATE INDEX cotos_datetime_end ON cotos(datetime_end);
CREATE INDEX cotos_repost_of_id ON cotos(repost_of_id);

-- Some columns of a repost should be the same values as the original.
CREATE TRIGGER cotos_reposts_sync AFTER UPDATE ON cotos BEGIN
  UPDATE cotos 
    SET 
      is_cotonoma = new.is_cotonoma, 
      updated_at = new.updated_at
    WHERE repost_of_id = new.uuid;
END;


--
-- A cotonoma is a specific type of coto in which other cotos are posted. 
--
CREATE TABLE cotonomas (
  -- Universally unique cotonoma ID.
  uuid TEXT NOT NULL PRIMARY KEY,

  -- UUID of the node in which this cotonoma was created.
  node_id TEXT NOT NULL,

  -- Coto UUID of this cotonoma.
  coto_id TEXT NOT NULL UNIQUE,

  -- Name of this cotonoma.
  name TEXT NOT NULL,

  created_at DATETIME NOT NULL, -- UTC
  updated_at DATETIME NOT NULL, -- UTC

  UNIQUE(node_id, name),
  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(coto_id) REFERENCES cotos(uuid) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE INDEX cotonomas_node_id ON cotonomas(node_id);
CREATE INDEX cotonomas_coto_id ON cotonomas(coto_id);
CREATE INDEX cotonomas_name ON cotonomas(name);

-- cotos.updated_at should be synced with corresponding cotonomas.updated_at
-- when cotonomas are updated.
CREATE TRIGGER cotonomas_cotos_sync AFTER UPDATE ON cotonomas BEGIN
  UPDATE cotos 
    SET updated_at = new.updated_at
    WHERE uuid = new.coto_id;
END;


--
-- An ito is a directed edge connecting two cotos.
--
CREATE TABLE itos (
  -- Universally unique ito ID.
  uuid TEXT NOT NULL PRIMARY KEY,

  -- UUID of the node in which this ito was created.
  node_id TEXT NOT NULL,

  -- UUID of the node whose owner has created this ito.
  created_by_id TEXT NOT NULL,

  -- UUID of the coto at the source of this ito.
  source_coto_id TEXT NOT NULL,

  -- UUID of the coto at the target of this ito.
  target_coto_id TEXT NOT NULL,

  -- Description of this ito.
  description TEXT,

  -- Content attached to this ito.
  details TEXT,

  -- Order of this ito among the ones from the same coto.
  "order" INTEGER NOT NULL,

  created_at DATETIME NOT NULL, -- UTC
  updated_at DATETIME NOT NULL, -- UTC

  UNIQUE(source_coto_id, target_coto_id),
  UNIQUE(source_coto_id, "order"),
  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(created_by_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(source_coto_id) REFERENCES cotos(uuid) ON DELETE CASCADE,
  FOREIGN KEY(target_coto_id) REFERENCES cotos(uuid) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE INDEX itos_node_id ON itos(node_id);
CREATE INDEX itos_created_by_id ON itos(created_by_id);
CREATE INDEX itos_source_coto_id ON itos(source_coto_id);
CREATE INDEX itos_target_coto_id ON itos(target_coto_id);
