package org.octopusden.octopus.dms

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.cloud.openfeign.EnableFeignClients

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
class DmsServiceApplication

fun main(args: Array<String>) {
    SpringApplication.run(DmsServiceApplication::class.java, *args)
}