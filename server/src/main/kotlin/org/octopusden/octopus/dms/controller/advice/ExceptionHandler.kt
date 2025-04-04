package org.octopusden.octopus.dms.controller.advice

import com.fasterxml.jackson.databind.ObjectMapper
import feign.FeignException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.octopusden.octopus.dms.client.common.dto.ApplicationErrorResponse
import org.octopusden.octopus.dms.exception.ArtifactAlreadyExistsException
import org.octopusden.octopus.dms.exception.ComponentIsNotRegisteredAsExplicitAndExternalException
import org.octopusden.octopus.dms.exception.DMSException
import org.octopusden.octopus.dms.exception.DownloadResultFailureException
import org.octopusden.octopus.dms.exception.GeneralArtifactStoreException
import org.octopusden.octopus.dms.exception.IllegalComponentRenamingException
import org.octopusden.octopus.dms.exception.IllegalVersionStatusException
import org.octopusden.octopus.dms.exception.NotFoundException
import org.octopusden.octopus.dms.exception.PackagingIsNotSpecifiedException
import org.octopusden.octopus.dms.exception.UnableToFindArtifactException
import org.octopusden.octopus.dms.exception.UnknownArtifactTypeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody

data class ErrorResponse(val statusCode: Int, val statusMessage: String, val message: String)

@ControllerAdvice
class ExceptionHandler(private val objectMapper: ObjectMapper) {
    @ExceptionHandler(
        GeneralArtifactStoreException::class,
        ComponentIsNotRegisteredAsExplicitAndExternalException::class,
        UnknownArtifactTypeException::class,
        ArtifactAlreadyExistsException::class,
        UnableToFindArtifactException::class,
        PackagingIsNotSpecifiedException::class,
        DownloadResultFailureException::class,
        IllegalVersionStatusException::class,
        IllegalComponentRenamingException::class
    )
    @Order(5)
    fun handle(request: HttpServletRequest, response: HttpServletResponse, e: DMSException) =
        createHttpResponse(request, e, HttpStatus.BAD_REQUEST)

    @ExceptionHandler(FeignException.NotFound::class)
    @Order(5)
    fun handle(request: HttpServletRequest, response: HttpServletResponse, e: FeignException.NotFound) =
        createHttpResponse(request, e, HttpStatus.BAD_REQUEST, "DMS-40011")

    @ExceptionHandler(NotFoundException::class)
    @Order(5)
    fun handle(request: HttpServletRequest, response: HttpServletResponse, e: NotFoundException) =
        createHttpResponse(request, e, HttpStatus.NOT_FOUND)


    @ExceptionHandler(org.octopusden.octopus.components.registry.core.exceptions.NotFoundException::class)
    @Order(5)
    fun handle(request: HttpServletRequest, response: HttpServletResponse, e: org.octopusden.octopus.components.registry.core.exceptions.NotFoundException) =
        createHttpResponse(request, e, HttpStatus.NOT_FOUND, "DMS-40011")

    @ExceptionHandler(AccessDeniedException::class)
    @Order(5)
    fun handle(request: HttpServletRequest, response: HttpServletResponse, e: AccessDeniedException) =
        createHttpResponse(request, e, HttpStatus.FORBIDDEN, "")

    @ExceptionHandler(UnsupportedOperationException::class)
    @ResponseBody
    @Order(5)
    fun handle(request: HttpServletRequest, response: HttpServletResponse, e: UnsupportedOperationException) =
        createHttpResponse(request, e, HttpStatus.NOT_IMPLEMENTED, e.message ?: "")

    @ExceptionHandler(Throwable::class)
    @ResponseBody
    @Order(10)
    fun handle(e: Throwable): ResponseEntity<ErrorResponse> {
        logger.error("Internal error", e)
        val httpStatus = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity(
            ErrorResponse(httpStatus.value(), httpStatus.reasonPhrase, e.message ?: "Unexpected exception"),
            httpStatus
        )
    }

    private fun createHttpResponse(
        request: HttpServletRequest,
        e: Exception,
        httpStatus: HttpStatus,
        code: String
    ): ResponseEntity<*> {
        return createHttpResponse(request.getHeader(HttpHeaders.ACCEPT), createResponse(code, e), httpStatus)
    }

    private fun createHttpResponse(
        request: HttpServletRequest,
        e: DMSException,
        httpStatus: HttpStatus
    ): ResponseEntity<*> {
        return createHttpResponse(request.getHeader(HttpHeaders.ACCEPT), createResponse(e), httpStatus)
    }

    private fun createHttpResponse(
        acceptHeader: String?,
        errorResponse: ApplicationErrorResponse,
        httpStatus: HttpStatus
    ): ResponseEntity<*> {
        return when {
            JSON_ACCEPT_HEADER_MEDIA_TYPES.any { acceptHeader?.contains(it) ?: true } -> {
                ResponseEntity(errorResponse, httpStatus)
            }

            else -> ResponseEntity(objectMapper.writeValueAsString(errorResponse), httpStatus)
        }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ExceptionHandler::class.java)
        private val JSON_ACCEPT_HEADER_MEDIA_TYPES = listOf(MediaType.APPLICATION_JSON_VALUE, "*/*")
        private fun createResponse(
            code: String,
            exception: Exception
        ): ApplicationErrorResponse {
            logger.error(exception.message, exception)
            return ApplicationErrorResponse(code, exception::class.java.name, exception.message ?: "")
        }

        private fun createResponse(exception: DMSException): ApplicationErrorResponse {
            logger.error(exception.message, exception)
            return ApplicationErrorResponse(
                exception.code,
                exception::class.java.name,
                exception.message ?: ""
            )
        }
    }
}
