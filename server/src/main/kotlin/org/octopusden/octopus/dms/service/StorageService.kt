package org.octopusden.octopus.dms.service

import org.octopusden.octopus.dms.entity.Artifact
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import java.io.InputStream

interface StorageService {
    fun getRepositoriesUrls(repositoryType: RepositoryType, includeStaging: Boolean): List<String>
    fun upload(artifact: Artifact, inputStream: InputStream)
    fun find(artifact: Artifact, includeStaging: Boolean): String
    fun download(artifact: Artifact, includeStaging: Boolean): InputStream
}
