package org.octopusden.octopus.dms.entity

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table

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
