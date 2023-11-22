package org.octopusden.octopus.dms.entity

import javax.persistence.*

@Entity
@Table(name = "component")
class Component(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,
        val name: String
) {
        override fun toString(): String {
                return "Component(id=$id, name=$name)"
        }
}
