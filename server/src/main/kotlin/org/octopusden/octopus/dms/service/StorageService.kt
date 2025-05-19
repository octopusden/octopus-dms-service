package org.octopusden.octopus.dms.service

import java.io.InputStream
import org.jfrog.artifactory.client.model.File
import org.octopusden.octopus.dms.client.common.dto.RepositoryType

interface StorageService {
    fun getRepositoriesUrls(repositoryType: RepositoryType, includeStaging: Boolean): List<String>
    fun upload(repositoryType: RepositoryType, path: String, inputStream: InputStream): File
    fun find(repositoryType: RepositoryType, includeStaging: Boolean, path: String): File?
    fun get(repositoryType: RepositoryType, includeStaging: Boolean, path: String): File
    fun download(repositoryType: RepositoryType, includeStaging: Boolean, path: String): InputStream
}
