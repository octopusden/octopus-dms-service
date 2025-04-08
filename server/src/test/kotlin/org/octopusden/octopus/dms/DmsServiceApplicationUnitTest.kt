package org.octopusden.octopus.dms

import com.fasterxml.jackson.core.type.TypeReference
import feign.Request
import feign.Response
import java.io.InputStream
import org.apache.http.entity.ContentType
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.octopusden.octopus.dms.client.DmsServiceUploadingClient
import org.octopusden.octopus.dms.client.common.dto.ApplicationErrorResponse
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactFullDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.ArtifactsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentRequestFilter
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentVersionsDTO
import org.octopusden.octopus.dms.client.common.dto.ComponentsDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.PropertiesDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.client.common.dto.VersionsDTO
import org.octopusden.octopus.dms.exception.DMSException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.util.LinkedMultiValueMap


@AutoConfigureMockMvc
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    classes = [DmsServiceApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("ut")
@WithMockUser(authorities = ["ROLE_DMS_USER_DEV"])
class DmsServiceApplicationUnitTest : DmsServiceApplicationBaseTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    override val client = object : DmsServiceUploadingClient {
        override fun getComponents(filter: ComponentRequestFilter): ComponentsDTO {
            val params = LinkedMultiValueMap<String, String>()
            params.setAll(objectMapper.convertValue(filter, object : TypeReference<Map<String, String>>() {}))
            return mockMvc.perform(
                MockMvcRequestBuilders.get("/rest/api/3/components")
                    .queryParams(params)
                    .accept(MediaType.APPLICATION_JSON)
            ).andReturn().response.toObject(object : TypeReference<ComponentsDTO>() {})
        }

        override fun getComponentMinorVersions(componentName: String) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/minor-versions")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<List<String>>() {})

        override fun getComponentVersions(
            componentName: String,
            minorVersion: String,
            includeRc: Boolean?
        ) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/versions?filter-by-minor=$minorVersion")
                .also {
                    if (includeRc != null) it.param("include-rc", includeRc.toString())
                }.accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<ComponentVersionsDTO>() {})

        override fun getComponentVersionDependencies(componentName: String, version: String) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/versions/$version/dependencies")
                .accept(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.toObject(object : TypeReference<List<ComponentVersionDTO>>() {})

        override fun getPreviousLinesLatestVersions(
            componentName: String,
            version: String,
            includeRc: Boolean?
        ) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/versions/$version/previous-lines-latest-versions")
                .also {
                    if (includeRc != null) it.param("include-rc", includeRc.toString())
                }.accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<VersionsDTO>() {})

        override fun getComponentVersionArtifacts(
            componentName: String,
            version: String,
            type: ArtifactType?
        ) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/versions/$version/artifacts").also {
                if (type != null) it.param("type", type.toString())
            }.accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<ArtifactsDTO>() {})

        override fun getComponentVersionArtifact(
            componentName: String,
            version: String,
            artifactId: Long
        ) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/versions/$version/artifacts/$artifactId")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<ArtifactFullDTO>() {})

        override fun downloadComponentVersionArtifact(
            componentName: String,
            version: String,
            artifactId: Long
        ) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/components/$componentName/versions/$version/artifacts/$artifactId/download")
                .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE)
        ).andReturn().response.toResponse()

        override fun registerComponentVersionArtifact(
            componentName: String,
            version: String,
            artifactId: Long,
            registerArtifactDTO: RegisterArtifactDTO,
            failOnAlreadyExists: Boolean?
        ) = mockMvc.perform(
            MockMvcRequestBuilders.post("/rest/api/3/components/$componentName/versions/$version/artifacts/$artifactId")
                .also {
                    if (failOnAlreadyExists != null) it.param("fail-on-already-exists", failOnAlreadyExists.toString())
                }
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(registerArtifactDTO))
                .accept(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.toObject(object : TypeReference<ArtifactFullDTO>() {})

        override fun deleteComponentVersionArtifact(componentName: String, version: String, artifactId: Long) =
            mockMvc.perform(
                MockMvcRequestBuilders.delete("/rest/api/3/components/$componentName/versions/$version/artifacts/$artifactId?dry-run=false")
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
            ).andReturn().response.processError()

        override fun renameComponent(componentName: String, newComponentName: String) = mockMvc.perform(
            MockMvcRequestBuilders.post("/rest/api/3/admin/rename-component/$componentName/$newComponentName")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.processError()

        override fun getConfiguration() = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/configuration")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<PropertiesDTO>() {})

        override fun getRepositories(repositoryType: RepositoryType) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/artifacts/repositories")
                .param("repository-type", repositoryType.name)
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<List<String>>() {})

        override fun getArtifact(id: Long) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/artifacts/$id")
                .accept(MediaType.APPLICATION_JSON)
        ).andReturn().response.toObject(object : TypeReference<ArtifactDTO>() {})

        override fun findArtifact(artifactCoordinates: ArtifactCoordinatesDTO) = mockMvc.perform(
            MockMvcRequestBuilders.post("/rest/api/3/artifacts/find")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(artifactCoordinates))
                .accept(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.toObject(object : TypeReference<ArtifactDTO>() {})

        override fun downloadArtifact(id: Long) = mockMvc.perform(
            MockMvcRequestBuilders.get("/rest/api/3/artifacts/$id/download")
                .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE, MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE)
        ).andReturn().response.toResponse()

        override fun addArtifact(
            artifactCoordinates: ArtifactCoordinatesDTO,
            failOnAlreadyExists: Boolean?
        ) = mockMvc.perform(
            MockMvcRequestBuilders.post("/rest/api/3/artifacts/add").also {
                if (failOnAlreadyExists != null) it.param("fail-on-already-exists", failOnAlreadyExists.toString())
            }
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(artifactCoordinates))
                .accept(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.toObject(object : TypeReference<ArtifactDTO>() {})

        override fun deleteArtifact(id: Long) = mockMvc.perform(
            MockMvcRequestBuilders.delete("/rest/api/3/artifacts/$id?dry-run=false")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.processError()

        override fun uploadArtifact(
            artifactCoordinates: MavenArtifactCoordinatesDTO,
            file: InputStream,
            fileName: String?,
            failOnAlreadyExists: Boolean?
        ) = mockMvc.perform(
            MockMvcRequestBuilders.multipart("/rest/api/3/artifacts/upload").also {
                if (failOnAlreadyExists != null) it.param("fail-on-already-exists", failOnAlreadyExists.toString())
            }
                .file(
                    MockMultipartFile(
                        "artifact",
                        "",
                        ContentType.APPLICATION_JSON.mimeType,
                        objectMapper.writeValueAsString(artifactCoordinates).toByteArray()
                    )
                )
                .file(
                    MockMultipartFile(
                        "file",
                        fileName ?: "",
                        ContentType.DEFAULT_BINARY.mimeType,
                        file
                    )
                )
                .accept(MediaType.APPLICATION_JSON)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        ).andReturn().response.toObject(object : TypeReference<MavenArtifactDTO>() {})


        private fun MockHttpServletResponse.processError() {
            if (this.status / 100 != 2) {
                throw try {
                    val error = objectMapper.readValue(this.contentAsByteArray, ApplicationErrorResponse::class.java)
                    DMSException.CODE_EXCEPTION_MAP[error.code]?.invoke(error.message)
                        ?: RuntimeException(error.message)
                } catch (e: Exception) {
                    RuntimeException(String(this.contentAsByteArray))
                }
            }
        }

        private fun <T> MockHttpServletResponse.toObject(typeReference: TypeReference<T>): T {
            this.processError()
            return objectMapper.readValue(this.contentAsByteArray, typeReference)
        }

        private fun MockHttpServletResponse.toResponse(): Response {
            return Response.builder()
                .request(Mockito.mock(Request::class.java))
                .status(this.status)
                .body(this.contentAsByteArray)
                .build()
        }
    }
}