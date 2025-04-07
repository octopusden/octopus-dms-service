package org.octopusden.octopus.dms.exception

abstract class DMSException(message: String, val code: String) : RuntimeException(message) {
    companion object {
        val CODE_EXCEPTION_MAP = mapOf(
            "DMS-40000" to { message: String -> GeneralArtifactStoreException(message) },
            "DMS-40001" to { message: String -> IllegalComponentTypeException(message) },
            "DMS-40002" to { message: String -> UnknownArtifactTypeException(message) },
            "DMS-40003" to { message: String -> ArtifactAlreadyExistsException(message) },
            "DMS-40006" to { message: String -> UnableToFindArtifactException(message) },
            "DMS-40007" to { message: String -> PackagingIsNotSpecifiedException(message) },
            "DMS-40008" to { message: String -> DownloadResultFailureException(message) },
            "DMS-40011" to { message: String -> NotFoundException(message) },
            "DMS-40012" to { message: String -> IllegalVersionStatusException(message) },
            "DMS-40013" to { message: String -> IllegalComponentRenamingException(message) },
            "DMS-40014" to { message: String -> PublishingException(message) }
        )
    }
}

class GeneralArtifactStoreException(message: String) : DMSException(message, "DMS-40000")
class IllegalComponentTypeException(message: String) : DMSException(message, "DMS-40001")
class UnknownArtifactTypeException(message: String) : DMSException(message, "DMS-40002")
class ArtifactAlreadyExistsException(message: String) : DMSException(message, "DMS-40003")
class UnableToFindArtifactException(message: String) : DMSException(message, "DMS-40006")
class PackagingIsNotSpecifiedException(message: String) : DMSException(message, "DMS-40007")
class DownloadResultFailureException(message: String) : DMSException(message, "DMS-40008")
class NotFoundException(message: String) : DMSException(message, "DMS-40011")
class IllegalVersionStatusException(message: String) : DMSException(message, "DMS-40012")
class IllegalComponentRenamingException(message: String) : DMSException(message, "DMS-40013")
class PublishingException(message: String) : DMSException(message, "DMS-40014")