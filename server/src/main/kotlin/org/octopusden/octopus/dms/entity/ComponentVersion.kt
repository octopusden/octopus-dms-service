package org.octopusden.octopus.dms.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "component_version")
class ComponentVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne
    val component: Component,
    var minorVersion: String,
    val version: String,
    var published: Boolean = false
) {
    override fun toString(): String {
        return "ComponentVersion(id=$id, component=$component, minorVersion=$minorVersion, version=$version, published=$published)"
    }
}
