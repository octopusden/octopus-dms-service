ALTER TABLE component_version
    ADD COLUMN IF NOT EXISTS release_version VARCHAR;

CREATE INDEX IF NOT EXISTS component_version_version_idx ON component_version (version);
CREATE INDEX IF NOT EXISTS component_version_minor_version_idx ON component_version (minor_version);
CREATE INDEX IF NOT EXISTS component_version_release_version_idx ON component_version (release_version);