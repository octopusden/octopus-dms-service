package org.octopusden.octopus.dms.client

import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsStatusesDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.client.common.dto.PropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import feign.CollectionFormat
import feign.Headers
import feign.Param
import feign.RequestLine
import feign.Response

interface DmsServiceFeignClient {
    @RequestLine("GET rest/api/3/components")
    fun getComponents(): ComponentsDTO

    @RequestLine("GET rest/api/3/components/{component-name}/minor-versions")
    fun getComponentMinorVersions(
        @Param("component-name") componentName: String
    ): List<String>

    @RequestLine(
        "GET rest/api/3/components/{component-name}/versions?filter-by-minor={filter-by-minor}&include-rc={include-rc}",
        collectionFormat = CollectionFormat.CSV
    )
    fun getComponentVersions(
        @Param("component-name") componentName: String,
        @Param("filter-by-minor") minorVersion: String,
        @Param("include-rc") includeRc: Boolean? = null
    ): ComponentVersionsStatusesDTO

    @RequestLine("GET rest/api/3/components/{component-name}/versions/{version}/previous-lines-latest-versions?include-rc={include-rc}")
    fun getPreviousLinesLatestVersions(
        @Param("component-name") componentName: String,
        @Param("version") version: String,
        @Param("include-rc") includeRc: Boolean? = null
    ): VersionsDTO

    @RequestLine("GET rest/api/3/components/{component-name}/versions/{version}/artifacts?type={type}")
    fun getComponentVersionArtifacts(
        @Param("component-name") componentName: String,
        @Param("version") version: String,
        @Param("type") type: ArtifactType? = null
    ): ArtifactsDTO

    @RequestLine("GET rest/api/3/components/{component-name}/versions/{version}/artifacts/{artifact-id}")
    fun getComponentVersionArtifact(
        @Param("component-name") componentName: String,
        @Param("version") version: String,
        @Param("artifact-id") artifactId: Long
    ): ArtifactFullDTO

    @RequestLine("GET rest/api/3/components/{component-name}/versions/{version}/artifacts/{artifact-id}/download")
    fun downloadComponentVersionArtifact(
        @Param("component-name") componentName: String,
        @Param("version") version: String,
        @Param("artifact-id") artifactId: Long
    ): Response

    @RequestLine("POST rest/api/3/components/{component-name}/versions/{version}/artifacts/{artifact-id}?fail-on-already-exists={fail-on-already-exists}")
    @Headers("Content-Type: application/json")
    fun registerComponentVersionArtifact(
        @Param("component-name") componentName: String,
        @Param("version") version: String,
        @Param("artifact-id") artifactId: Long,
        registerArtifactDTO: RegisterArtifactDTO,
        @Param("fail-on-already-exists") failOnAlreadyExists: Boolean? = null
    ): ArtifactFullDTO

    @RequestLine("DELETE rest/api/3/components/{component-name}/versions/{version}/artifacts/{artifact-id}?dry-run=false")
    fun deleteComponentVersionArtifact(
        @Param("component-name") componentName: String,
        @Param("version") version: String,
        @Param("artifact-id") artifactId: Long
    )

    @RequestLine("GET rest/api/3/configuration")
    fun getConfiguration(): PropertiesDTO

    @RequestLine("GET rest/api/3/artifacts/repositories?repository-type={repository-type}")
    fun getRepositories(
        @Param("repository-type") repositoryType: RepositoryType
    ): List<String>

    @RequestLine("GET rest/api/3/artifacts/{id}")
    fun getArtifact(
        @Param("id") id: Long
    ): ArtifactDTO

    @RequestLine("POST rest/api/3/artifacts/find")
    @Headers("Content-Type: application/json")
    fun findArtifact(
        artifactCoordinates: ArtifactCoordinatesDTO
    ): ArtifactDTO

    @RequestLine("GET rest/api/3/artifacts/{id}/download")
    fun downloadArtifact(
        @Param("id") id: Long
    ): Response

    @RequestLine("POST rest/api/3/artifacts/add?fail-on-already-exists={fail-on-already-exists}")
    @Headers("Content-Type: application/json")
    fun addArtifact(
        artifactCoordinates: ArtifactCoordinatesDTO,
        @Param("fail-on-already-exists") failOnAlreadyExists: Boolean? = null
    ): ArtifactDTO

    @RequestLine("DELETE rest/api/3/artifacts/{id}?dry-run=false")
    fun deleteArtifact(
        @Param("id") id: Long
    )
}
