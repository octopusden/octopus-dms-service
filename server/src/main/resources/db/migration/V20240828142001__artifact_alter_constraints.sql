ALTER TABLE artifact
DROP CONSTRAINT path_check;

ALTER TABLE artifact
    ADD CONSTRAINT path_check CHECK (substring(path, 1, 1) <> '/' AND (
                repository_type = 'MAVEN' OR
                repository_type = 'DOCKER' OR
                (repository_type = 'DEBIAN' AND substring(path, char_length(path) - 3) = '.deb') OR
                (repository_type = 'RPM' AND substring(path, char_length(path) - 3) = '.rpm')
        ));

ALTER TABLE artifact ADD COLUMN image VARCHAR;
COMMENT ON COLUMN artifact.image IS 'The image name of the docker artifact.';

ALTER TABLE artifact ADD COLUMN tag VARCHAR;
COMMENT ON COLUMN artifact.tag IS 'The tag of the docker artifact.';

ALTER TABLE artifact
    ADD CONSTRAINT image_check CHECK (
            repository_type <> 'DOCKER' OR image IS NOT NULL
        );

ALTER TABLE artifact
    ADD CONSTRAINT tag_check CHECK (
            repository_type <> 'DOCKER' OR tag IS NOT NULL
        );