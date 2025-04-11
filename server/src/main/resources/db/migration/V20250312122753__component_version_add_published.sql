ALTER TABLE component_version ADD COLUMN published BOOL NOT NULL DEFAULT false;

ALTER TABLE component_version ALTER COLUMN published DROP DEFAULT;