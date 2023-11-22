package org.octopusden.octopus.dms.entity

import javax.persistence.*

@Entity
@Table(name = "component_version")
class ComponentVersion(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,
        @ManyToOne
        val component: Component,
        var minorVersion: String,
        val version: String
) {
    override fun toString(): String {
        return "ComponentVersion(id=$id, component=$component, minorVersion=$minorVersion, version=$version)"
    }
}
