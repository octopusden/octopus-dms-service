package org.octopusden.octopus.dms.configuration

import org.octopusden.octopus.dms.exception.UnknownArtifactTypeException
import org.octopusden.octopus.dms.client.common.dto.ArtifactType
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
//        registry.addRedirectViewController("/", "swagger-ui/index.html")
        registry.addRedirectViewController("/", "index.html")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("public/**").addResourceLocations("classpath:/public/")
    }

    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.isUseSuffixPatternMatch = true
        configurer.isUseRegisteredSuffixPatternMatch = false
    }

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(object : Converter<String, ArtifactType> {
            override fun convert(source: String): ArtifactType {
                return ArtifactType.findByType(source)
                    ?: throw UnknownArtifactTypeException(
                        "Unable detect type $source. Available types is ${ArtifactType.values().map { it.type }}"
                    )
            }
        })
    }
}
