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

  -- Number of outgoing links from this coto.
  outgoing_links INTEGER DEFAULT 0 NOT NULL,

  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(posted_in_id) REFERENCES cotonomas(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(posted_by_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(repost_of_id) REFERENCES cotos(uuid) ON DELETE RESTRICT
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

  -- Number of posts in this cotonoma.
  posts INTEGER DEFAULT 0 NOT NULL,

  UNIQUE(node_id, name),
  FOREIGN KEY(node_id) REFERENCES nodes(uuid) ON DELETE RESTRICT,
  FOREIGN KEY(coto_id) REFERENCES cotos(uuid) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE INDEX cotonomas_node_id ON cotonomas(node_id);
CREATE INDEX cotonomas_coto_id ON cotonomas(coto_id);


--
-- A link is a directed edge connecting two cotos.
--
CREATE TABLE links (
  -- Universally unique link ID.
  uuid TEXT NOT NULL PRIMARY KEY,

  -- UUID of the node in which this link was created.
  node_id TEXT NOT NULL,

  -- UUID of the cotonoma in which this link was created,
  -- or NULL if it does not belong to a cotonoma.
  --
  -- This column is used to fetch links in a cotonoma, intended as a complement to a graph traversal.
  -- cf. https://github.com/cotoami/cotoami/blob/develop/lib/cotoami/services/coto_graph_service.ex#L55-L59
  created_in_id TEXT,

  -- UUID of the node whose owner has created this link.
  created_by_id TEXT NOT NULL,

  -- UUID of the coto at the source of this link.
  source_coto_id TEXT NOT NULL,

  -- UUID of the coto at the target of this link.
  target_coto_id TEXT NOT NULL,

  -- Linkng phrase to express the relationship between the two cotos.
  linking_phrase TEXT,

  -- Content attached to this link.
  details TEXT,

  -- Order of this link among the ones from the same coto.
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

CREATE INDEX links_node_id ON links(node_id);
CREATE INDEX links_created_by_id ON links(created_by_id);
CREATE INDEX links_source_coto_id ON links(source_coto_id);
CREATE INDEX links_target_coto_id ON links(target_coto_id);
