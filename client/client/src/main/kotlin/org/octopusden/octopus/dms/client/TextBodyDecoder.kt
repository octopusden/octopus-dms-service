package org.octopusden.octopus.dms.client

import feign.Response
import feign.codec.Decoder
import java.lang.reflect.Type

class TextBodyDecoder(private val delegate: Decoder) : Decoder {
    override fun decode(response: Response, type: Type): Any {
        return if (String::class == type || String::class.java == type) {
            String(response.body().asInputStream().readBytes())
        } else {
            delegate.decode(response, type)
        }
    }
}
