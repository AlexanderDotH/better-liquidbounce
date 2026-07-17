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
import org.w3c.dom.Element
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HMAC_SHA256 = "HmacSHA256"

internal data class FritzBoxSessionInfo(
    val sid: String,
    val challenge: String?,
    val defaultUser: String?
)

internal fun configuredFritzBoxSid() =
    configuredValue("LIQUIDBOUNCE_FRITZBOX_SID", "liquidbounce.fritzbox.sid")
        ?.takeIf { it.isValidFritzBoxSid() }

internal fun configuredFritzBoxUser() =
    configuredValue("LIQUIDBOUNCE_FRITZBOX_USER", "liquidbounce.fritzbox.user")

internal fun configuredFritzBoxPassword() =
    configuredValue("LIQUIDBOUNCE_FRITZBOX_PASSWORD", "liquidbounce.fritzbox.password")

internal fun createFritzBoxLoginResponse(challenge: String, password: String): String {
    val challengeParts = challenge.split("$")
    if (challengeParts.firstOrNull() == "2" && challengeParts.size == 5) {
        return createPbkdf2LoginResponse(challengeParts, password)
    }

    return "$challenge-${md5Hex("$challenge-$password".toByteArray(StandardCharsets.UTF_16LE))}"
}

internal fun Document.defaultFritzBoxUser(): String? {
    val users = getElementsByTagName("User")
    var firstUser: String? = null

    for (index in 0 until users.length) {
        val element = users.item(index) as? Element ?: continue
        val username = element.textContent.takeIf { it.isNotBlank() } ?: continue

        firstUser = firstUser ?: username
        if (element.getAttribute("last") == "1") {
            return username
        }
    }

    return firstUser
}

private fun configuredValue(environmentName: String, propertyName: String) =
    (System.getenv(environmentName) ?: System.getProperty(propertyName))
        ?.takeIf { it.isNotBlank() }

private fun createPbkdf2LoginResponse(challengeParts: List<String>, password: String): String {
    val firstHash = pbkdf2HmacSha256(
        password.toByteArray(StandardCharsets.UTF_8),
        challengeParts[2].hexToBytes(),
        challengeParts[1].toInt()
    )
    val secondHash = pbkdf2HmacSha256(
        firstHash,
        challengeParts[4].hexToBytes(),
        challengeParts[3].toInt()
    )

    return "${challengeParts[4]}${'$'}${secondHash.toHexString()}"
}

private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
    require(iterations > 0) { "PBKDF2 iteration count must be positive" }

    val mac = Mac.getInstance(HMAC_SHA256)
        .apply { init(SecretKeySpec(password, HMAC_SHA256)) }
    var block = ByteArray(salt.size + 4).also {
        salt.copyInto(it)
        it[it.lastIndex] = 1
    }
    val result = ByteArray(mac.macLength)

    repeat(iterations) {
        block = mac.doFinal(block)
        result.indices.forEach { index ->
            result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
        }
    }

    return result
}

private fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).toHexString()

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Invalid hex value length" }

    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { "%02x".format(it) }
