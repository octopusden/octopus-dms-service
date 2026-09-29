ALTER TABLE artifact
DROP CONSTRAINT path_check;

ALTER TABLE artifact
DROP CONSTRAINT artifact_unique;

ALTER TABLE artifact
    ADD CONSTRAINT path_check CHECK (substring(path, 1, 1) <> '/' AND (
                repository_type = 'MAVEN' OR
                repository_type = 'DOCKER' OR
                repository_type = 'GENERIC' OR
                (repository_type = 'DEBIAN' AND substring(path, char_length(path) - 3) = '.deb') OR
                (repository_type = 'RPM' AND substring(path, char_length(path) - 3) = '.rpm')
        ));

ALTER TABLE artifact
    ADD CONSTRAINT artifact_unique UNIQUE (repository_type, path)