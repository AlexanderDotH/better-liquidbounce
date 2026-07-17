/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.features

import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

private const val INVALID_FRITZ_BOX_SID = "0000000000000000"

internal fun parseXml(body: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }

    return factory.newDocumentBuilder()
        .parse(ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8)))
}

internal fun Document.textContentOf(tagName: String): String? =
    getElementsByTagName(tagName).item(0)?.textContent

internal fun HttpResponse<String>.requireSuccessfulResponse(action: String): String {
    if (statusCode() !in 200..299) {
        error("$action failed with HTTP ${statusCode()}")
    }

    return body()
}

internal fun String.isValidFritzBoxSid(): Boolean =
    length == 16 && this != INVALID_FRITZ_BOX_SID && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

internal fun String.isUsableIp(): Boolean =
    isNotBlank() && this != "0.0.0.0" && this != "::" && this != "::0"

internal fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)
