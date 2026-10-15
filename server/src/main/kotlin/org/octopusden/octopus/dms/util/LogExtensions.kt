package org.octopusden.octopus.dms.util

fun String.sanitizeForLog(): String = replace('\r', '_').replace('\n', '_')
