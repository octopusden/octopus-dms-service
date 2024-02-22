package org.octopusden.octopus.dms.client.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.octopusden.octopus.dms.client.DmsClientErrorDecoder
import org.octopusden.octopus.dms.client.DmsServiceFeignClient
import org.octopusden.octopus.dms.client.DmsServiceUploadingClient
import org.octopusden.octopus.dms.client.TextBodyDecoder
import org.octopusden.octopus.dms.client.common.dto.ApplicationErrorResponse
import org.octopusden.octopus.dms.client.common.dto.ArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactCoordinatesDTO
import org.octopusden.octopus.dms.client.common.dto.MavenArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RegisterArtifactDTO
import org.octopusden.octopus.dms.client.common.dto.RepositoryType
import org.octopusden.octopus.dms.exception.DMSException
import feign.Feign
import feign.Logger
import feign.Request
import feign.form.FormEncoder
import feign.httpclient.ApacheHttpClient
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder
import feign.slf4j.Slf4jLogger
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.HttpClientBuilder
import org.octopusden.octopus.dms.client.common.dto.ComponentDTO

class ClassicDmsServiceClient(
    private val parametersProvider: DmsServiceClientParametersProvider, private val objectMapper: ObjectMapper
) : DmsServiceUploadingClient {
    private val httpClient = HttpClientBuilder.create().build()
    private val client = createClient(parametersProvider)

    constructor(parametersProvider: DmsServiceClientParametersProvider) : this(
        parametersProvider, getMapper()
    )

    override fun getComponents() = client.getComponents()

    override fun getComponentMinorVersions(
        componentName: String
    ) = client.getComponentMinorVersions(componentName)

    override fun getComponentVersions(
        componentName: String, minorVersion: String, includeRc: Boolean?
    ) = client.getComponentVersions(componentName, minorVersion, includeRc)

    override fun getPreviousLinesLatestVersions(
        componentName: String, version: String, includeRc: Boolean?
    ) = client.getPreviousLinesLatestVersions(componentName, version, includeRc)

    override fun getComponentVersionArtifacts(
        componentName: String, version: String, type: ArtifactType?
    ) = client.getComponentVersionArtifacts(componentName, version, type)

    override fun getComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ) = client.getComponentVersionArtifact(componentName, version, artifactId)

    override fun downloadComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ) = client.downloadComponentVersionArtifact(componentName, version, artifactId)

    override fun registerComponentVersionArtifact(
        componentName: String,
        version: String,
        artifactId: Long,
        registerArtifactDTO: RegisterArtifactDTO,
        failOnAlreadyExists: Boolean?
    ) = client.registerComponentVersionArtifact(
        componentName,
        version,
        artifactId,
        registerArtifactDTO,
        failOnAlreadyExists
    )

    override fun deleteComponentVersionArtifact(
        componentName: String, version: String, artifactId: Long
    ) = client.deleteComponentVersionArtifact(componentName, version, artifactId)

    override fun renameComponent(componentName: String, newComponentName: String, dryRun: Boolean) =
        client.renameComponent(componentName, newComponentName)

    override fun getConfiguration() = client.getConfiguration()

    override fun getRepositories(
        repositoryType: RepositoryType
    ) = client.getRepositories(repositoryType)

    override fun getArtifact(
        id: Long
    ) = client.getArtifact(id)

    override fun findArtifact(
        artifactCoordinates: ArtifactCoordinatesDTO
    ) = client.findArtifact(artifactCoordinates)

    override fun downloadArtifact(
        id: Long
    ) = client.downloadArtifact(id)

    override fun addArtifact(
        artifactCoordinates: ArtifactCoordinatesDTO, failOnAlreadyExists: Boolean?
    ) = client.addArtifact(artifactCoordinates, failOnAlreadyExists)

    override fun deleteArtifact(
        id: Long
    ) = client.deleteArtifact(id)

    override fun uploadArtifact(
        artifactCoordinates: MavenArtifactCoordinatesDTO,
        file: InputStream,
        fileName: String?,
        failOnAlreadyExists: Boolean?
    ): MavenArtifactDTO {
        val httpEntity = MultipartEntityBuilder.create().addPart(
            "artifact",
            StringBody(objectMapper.writeValueAsString(artifactCoordinates), ContentType.APPLICATION_JSON)
        ).addBinaryBody("file", file, ContentType.DEFAULT_BINARY, fileName).build()
        val httpPost = HttpPost(
            "${parametersProvider.getApiUrl()}/rest/api/3/artifacts/upload" +
                    if (failOnAlreadyExists != null) "?fail-on-already-exists=$failOnAlreadyExists" else ""
        )
        httpPost.entity = httpEntity
        httpPost.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.mimeType)
        httpPost.addHeader(HttpHeaders.AUTHORIZATION, getAuthHeader())
        val response = httpClient.execute(httpPost)
        if (response.statusLine.statusCode / 100 != 2) {
            response.entity.content.use {
                val responseBody = String(it.readBytes())
                if (response.allHeaders.any { header ->
                        header.name == HttpHeaders.CONTENT_TYPE && header.elements.any { headerElement ->
                            headerElement.name.contains(ContentType.APPLICATION_JSON.mimeType)
                        }
                    }) {
                    try {
                        objectMapper.readValue(responseBody, ApplicationErrorResponse::class.java)
                    } catch (_: Exception) {
                        null
                    }?.let { error ->
                        throw DMSException.CODE_EXCEPTION_MAP[error.code]?.invoke(error.message)
                            ?: RuntimeException(error.message)
                    }
                }
                throw RuntimeException(responseBody)
            }
        }
        return response.entity.content.use { objectMapper.readValue(it, MavenArtifactDTO::class.java) }
    }

    private fun createClient(parametersProvider: DmsServiceClientParametersProvider): DmsServiceFeignClient {
        val jacksonEncoder = JacksonEncoder(objectMapper)
        return Feign.builder().client(ApacheHttpClient(httpClient))
            .options(Request.Options(5, TimeUnit.MINUTES, 5, TimeUnit.MINUTES, true))
            .encoder(FormEncoder(jacksonEncoder)).decoder(TextBodyDecoder(JacksonDecoder(objectMapper)))
            .errorDecoder(DmsClientErrorDecoder(objectMapper)).requestInterceptor { requestTemplate ->
                val authHeader = getAuthHeader()
                requestTemplate.header(HttpHeaders.AUTHORIZATION, authHeader)
            }.logger(Slf4jLogger(DmsServiceFeignClient::class.java)).logLevel(Logger.Level.BASIC)
            .target(DmsServiceFeignClient::class.java, parametersProvider.getApiUrl())
    }

    private fun getAuthHeader(): String {
        val authHeader = parametersProvider.getBearerToken()?.let { token ->
            if (token.isNotBlank()) {
                "Bearer $token"
            } else {
                null
            }
        } ?: parametersProvider.getBasicCredentials()?.let { basicCredentials ->
            if (basicCredentials.replace(":", "").isNotBlank()) {
                "Basic ${
                    base64Encoder.encodeToString(basicCredentials.toByteArray(Charset.forName(Charsets.UTF_8.name())))
                }"
            } else {
                null
            }
        } ?: throw IllegalArgumentException("Bearer token or basic credentials must be provided")
        return authHeader
    }

    companion object {
        private val base64Encoder = Base64.getEncoder()
        private fun getMapper(): ObjectMapper {
            val objectMapper = ObjectMapper()
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            objectMapper.registerModule(KotlinModule.Builder().build())
            return objectMapper
        }
    }
}
