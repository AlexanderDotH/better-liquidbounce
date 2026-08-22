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
package net.ccbluex.liquidbounce.features.module.modules.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaceClipResearchProfileTest {

    @Test
    fun `Paper 26_2 lab profile remains pinned and explicitly unvalidated`() {
        val profile = MaceClipResearchProfiles.PAPER_26_2_BUILD_112

        assertEquals(MaceClipResearchValidation.UNVALIDATED, profile.validation)
        assertEquals("26.2", profile.minecraftVersion)
        assertEquals(776, profile.protocolVersion)
        assertEquals(112, profile.paperBuildId)
        assertEquals(25, profile.javaVersion)
        assertEquals(
            "bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e",
            profile.paperSha256,
        )
        assertEquals(
            "b84faf38c6db14618a71bc31409be3e36e52832bb92aed472e8bca517a25076c",
            profile.plugins.single().sha256,
        )
    }
}
