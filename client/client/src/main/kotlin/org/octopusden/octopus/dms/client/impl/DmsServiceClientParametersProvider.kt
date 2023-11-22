package org.octopusden.octopus.dms.client.impl

interface DmsServiceClientParametersProvider {
    fun getApiUrl(): String
    fun getBearerToken(): String?
    fun getBasicCredentials(): String?
}