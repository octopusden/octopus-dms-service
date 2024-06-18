package org.octopusden.octopus.dms.controller.ui

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("ui/search")
class SearchController {

    @GetMapping
    fun search(@RequestParam("query", required = true) query: String): List<Map<String, String>> {
        Thread.sleep(1500)
        return listOf()
    }
}
