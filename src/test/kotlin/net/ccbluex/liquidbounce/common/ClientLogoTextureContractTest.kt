/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.ccbluex.liquidbounce.common

import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class ClientLogoTextureContractTest {
    @Test
    fun `logo identifier keeps the public liquidbounce namespace`() {
        assertEquals(Identifier.fromNamespaceAndPath("liquidbounce", "logo"), ClientLogoTexture.CLIENT_LOGO)
    }

    @Test
    fun `logo class loader resolves the unchanged png bytes`() {
        val resource = ClientLogoTexture::class.java.getResourceAsStream(LOGO_RESOURCE)
        assertNotNull(resource)

        val hash = resource!!.use { stream ->
            MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()).toHexString()
        }
        assertEquals(LOGO_SHA256, hash)
    }

    private fun ByteArray.toHexString() = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val LOGO_RESOURCE = "/resources/liquidbounce/logo_banner.png"
        const val LOGO_SHA256 = "a8b9af8e7540370534ccaa5fb0fe10f82392be4f4de050a340df972088557edf"
    }
}
