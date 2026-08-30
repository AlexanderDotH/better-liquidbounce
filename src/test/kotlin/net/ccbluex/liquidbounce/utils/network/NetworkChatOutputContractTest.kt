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
package net.ccbluex.liquidbounce.utils.network

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkChatOutputContractTest {

    @Test
    fun `network utilities publish failures through the lower-owned chat port`() {
        val sneakingSource = source("Send1_21_5StartSneaking.kt")
        val useItemSource = source("UseItem.kt")

        assertFalse("net.ccbluex.liquidbounce.features.chat" in sneakingSource)
        assertFalse("net.ccbluex.liquidbounce.features.chat" in useItemSource)
        assertTrue("import net.ccbluex.liquidbounce.common.chat.ClientChatOutput" in sneakingSource)
        assertTrue("ClientChatOutput.publish(markAsError(" in sneakingSource)
    }

    private fun source(name: String): String = Files.readString(SOURCE_ROOT.resolve(name))

    private companion object {
        val SOURCE_ROOT: Path = Path.of("src/main/kotlin/net/ccbluex/liquidbounce/utils/network")
    }
}
