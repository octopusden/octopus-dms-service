package org.octopusden.octopus.dms.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.octopusden.octopus.dms.client.common.dto.ApplicationErrorResponse
import org.octopusden.octopus.dms.exception.DMSException
import feign.Response
import feign.codec.ErrorDecoder
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType

class DmsClientErrorDecoder(private val objectMapper: ObjectMapper) : ErrorDecoder.Default() {
    override fun decode(methodKey: String?, response: Response?): Exception =
        response?.let {
            val responseBody = it.body()?.asInputStream()?.use { inputStream ->
                String(inputStream.readBytes())
            }
            if (responseBody != null && it.headers()[HttpHeaders.CONTENT_TYPE]?.contains(ContentType.APPLICATION_JSON.mimeType) == true) {
                try {
                    val error = objectMapper.readValue(responseBody, ApplicationErrorResponse::class.java)
                    return@let DMSException.CODE_EXCEPTION_MAP[error.code]?.invoke(error.message)
                        ?: RuntimeException(error.message)
                } catch (_: Exception) {
                }
            }
            return@let super.decode(methodKey, it)
        } ?: super.decode(methodKey, null)
}
