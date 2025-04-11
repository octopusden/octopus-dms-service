package org.octopusden.octopus.dms.dto

data class BuildDTO(val component: String, val version: String) {
    override fun toString() = "$component:$version"
}
