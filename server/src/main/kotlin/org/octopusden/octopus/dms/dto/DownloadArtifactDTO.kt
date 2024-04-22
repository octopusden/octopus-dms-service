package org.octopusden.octopus.dms.dto

import java.io.InputStream

class DownloadArtifactDTO(
    val fileName: String,
    val file: InputStream
)