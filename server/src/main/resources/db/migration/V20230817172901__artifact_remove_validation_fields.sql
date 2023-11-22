CREATE TABLE artifact
(
    id              BIGSERIAL PRIMARY KEY,
    repository_type VARCHAR NOT NULL,
    path            VARCHAR NOT NULL,
    uploaded        BOOL    NOT NULL,
    group_id        VARCHAR,
    artifact_id     VARCHAR,
    version         VARCHAR,
    packaging       VARCHAR,
    classifier      VARCHAR
);

ALTER TABLE artifact
    ADD CONSTRAINT artifact_unique UNIQUE (path);

ALTER TABLE artifact
    ADD CONSTRAINT path_check CHECK (substring(path, 1, 1) <> '/' AND (
                repository_type = 'MAVEN' OR
                (repository_type = 'DEBIAN' AND substring(path, char_length(path) - 3) = '.deb') OR
                (repository_type = 'RPM' AND substring(path, char_length(path) - 3) = '.rpm')
        ));

ALTER TABLE artifact
    ADD CONSTRAINT group_id_check CHECK (repository_type <> 'MAVEN' OR group_id IS NOT NULL);

ALTER TABLE artifact
    ADD CONSTRAINT artifact_id_check CHECK (repository_type <> 'MAVEN' OR artifact_id IS NOT NULL);

ALTER TABLE artifact
    ADD CONSTRAINT version_check CHECK (repository_type <> 'MAVEN' OR version IS NOT NULL);

ALTER TABLE artifact
    ADD CONSTRAINT packaging_check CHECK (repository_type <> 'MAVEN' OR packaging IS NOT NULL);

CREATE TABLE component
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR NOT NULL UNIQUE
);

CREATE TABLE component_version
(
    id            BIGSERIAL PRIMARY KEY,
    component_id  BIGINT  NOT NULL REFERENCES component (id) ON DELETE CASCADE,
    minor_version VARCHAR NOT NULL,
    version       VARCHAR NOT NULL
);

CREATE UNIQUE INDEX component_version_uq_idx ON component_version (component_id, version);

CREATE TABLE component_version_artifact
(
    id                   BIGSERIAL PRIMARY KEY,
    component_version_id BIGINT  NOT NULL REFERENCES component_version (id) ON DELETE CASCADE,
    artifact_id          BIGINT  NOT NULL REFERENCES artifact (id) ON DELETE CASCADE,
    type                 VARCHAR NOT NULL
);

ALTER TABLE component_version_artifact
    ADD CONSTRAINT component_version_artifact_unique UNIQUE (component_version_id, artifact_id);
