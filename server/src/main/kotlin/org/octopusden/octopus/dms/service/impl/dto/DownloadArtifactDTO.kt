package org.octopusden.octopus.dms.service.impl.dto

import java.io.InputStream

class DownloadArtifactDTO(
    val fileName: String,
    val file: InputStream
)