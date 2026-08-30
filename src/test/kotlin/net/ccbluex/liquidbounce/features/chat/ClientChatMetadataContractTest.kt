/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.features.command.Command
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClientChatMetadataContractTest {

    @Test
    fun `module metadata keeps its stable message id`() {
        assertEquals("MNoFall#info", MessageMetadata.byModule(ValueGroup("NoFall")).id)
    }

    @Test
    fun `command metadata keeps its stable message id`() {
        val command = Command("config", emptyList(), emptyList(), emptyList(), false, null, false)

        assertEquals("Cconfig#info", MessageMetadata.byCommand(command).id)
    }
}
