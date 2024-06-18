package org.octopusden.octopus.dms.controller.ui

import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse
import org.octopusden.octopus.dms.client.common.dto.ArtifactShortDTO
import org.octopusden.octopus.dms.controller.ArtifactController
import org.octopusden.octopus.dms.service.ArtifactService
import org.octopusden.octopus.dms.service.ComponentService

@RestController
@RequestMapping("ui/artifacts")
class UiArtifactController(
    private val artifactService: ArtifactService,
    private val componentService: ComponentService,
    private val artifactController: ArtifactController
) {

    @GetMapping("component/{component}/version/{version}")
    fun getArtifacts(
        @PathVariable("component") component: String,
        @PathVariable("version") version: String,
    ): List<ArtifactShortDTO> = componentService.getComponentVersionArtifacts(component, version, null)
        .artifacts

    @GetMapping("component/{component}/version/{version}/{id}")
    fun getArtifact(
        @PathVariable("component") component: String,
        @PathVariable("version") version: String,
        @PathVariable("id") id: Long,
        response: HttpServletResponse,
    ) {
        artifactController.download(id, response)
    }

    @DeleteMapping("component/{component}/version/{version}/{id}")
    fun deleteArtifact(
        @PathVariable("component") component: String,
        @PathVariable("version") version: String,
        @PathVariable("id") id: Long
    ) {
        artifactService.delete(id, false)
    }
}
