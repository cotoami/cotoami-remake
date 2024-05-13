--
-- Virtual tables that store FTS full-text index entries.
-- ref. https://sqlite.org/fts5.html
--

-- FTS full-text index mainly for English language terms.
CREATE VIRTUAL TABLE cotos_fts USING fts5(
  content,
  summary,

  -- Columns that are not added to the FTS index and to be retrieved from the external content table.
  uuid UNINDEXED,
  node_id UNINDEXED,
  posted_in_id UNINDEXED,
  posted_by_id UNINDEXED,
  is_cotonoma UNINDEXED,
  repost_of_id UNINDEXED,
  reposted_in_ids UNINDEXED,
  created_at UNINDEXED,
  updated_at UNINDEXED,
  outgoing_links UNINDEXED,

  --
  -- Configuration options
  -- 

  -- Tokenizers:
  -- Porter Tokenizer:
  --   This allows search terms like "correction" to match similar words such as "corrected" or "correcting". 
  --   The porter stemmer algorithm is designed for use with English language terms only.
  -- Unicode61 Tokenizer:
  --   The default tokenizer. By default all space and punctuation characters, as defined by Unicode 6.1, 
  --   are considered separators, and all other characters as token characters.
  --   `remove_diacritics 2`: 
  --     Diacritics are removed from all Latin script characters.
  --     This means, for example, that "A", "a", "À", "à", "Â" and "â" are all considered to be equivalent.
  tokenize = 'porter unicode61 remove_diacritics 2',

  -- External content:
  -- The "content" option may be used to create an FTS5 table that stores only FTS full-text index entries.
  -- Whenever column values are required by FTS5, it queries the external content table.
  -- (By default, when a row is inserted into an FTS5 table, in addition to building the index, 
  -- FTS5 makes a copy of the original row content.)
  content=cotos,  -- an "external content" table.
  content_rowid=rowid -- used to retrieve column values for documents that match a FTS query.
);

-- FTS full-text index for languages that can't be tokenized correctly by the default unicode61 tokenizer.
CREATE VIRTUAL TABLE cotos_fts_trigram USING fts5(
  content,
  summary,

  uuid UNINDEXED,
  node_id UNINDEXED,
  posted_in_id UNINDEXED,
  posted_by_id UNINDEXED,
  is_cotonoma UNINDEXED,
  repost_of_id UNINDEXED,
  reposted_in_ids UNINDEXED,
  created_at UNINDEXED,
  updated_at UNINDEXED,
  outgoing_links UNINDEXED,

  -- The trigram tokenizer extends FTS5 to support substring matching in general, 
  -- instead of the usual token matching. When using the trigram tokenizer, 
  -- a query or phrase token may match any sequence of characters within a row, not just a complete token. 
  tokenize = 'trigram',

  content=cotos, 
  content_rowid=rowid
);


--
-- Triggers to keep the FTS index up to date.
--

CREATE TRIGGER cotos_fts_insert AFTER INSERT ON cotos BEGIN
  INSERT INTO cotos_fts(rowid, content, summary) 
    VALUES (new.rowid, new.content, new.summary);
  INSERT INTO cotos_fts_trigram(rowid, content, summary) 
    VALUES (new.rowid, new.content, new.summary);
END;

-- https://sqlite.org/fts5.html#the_delete_command
-- When a document is inserted into the FTS5 table, an entry is added to the full-text index to 
-- record the position of each token within the new document. When a document is removed, 
-- the original data is required in order to determine the set of entries that need to be removed 
-- from the full-text index. 
CREATE TRIGGER cotos_fts_delete AFTER DELETE ON cotos BEGIN
  INSERT INTO cotos_fts(cotos_fts, rowid, content, summary) 
    VALUES('delete', old.rowid, old.content, old.summary);
  INSERT INTO cotos_fts_trigram(cotos_fts_trigram, rowid, content, summary) 
    VALUES('delete', old.rowid, old.content, old.summary);
END;

CREATE TRIGGER cotos_fts_update AFTER UPDATE ON cotos BEGIN
  INSERT INTO cotos_fts(cotos_fts, rowid, content, summary) 
    VALUES('delete', old.rowid, old.content, old.summary);
  INSERT INTO cotos_fts(rowid, content, summary) 
    VALUES (new.rowid, new.content, new.summary);

  INSERT INTO cotos_fts_trigram(cotos_fts_trigram, rowid, content, summary) 
    VALUES('delete', old.rowid, old.content, old.summary);
  INSERT INTO cotos_fts_trigram(rowid, content, summary) 
    VALUES (new.rowid, new.content, new.summary);
END;
