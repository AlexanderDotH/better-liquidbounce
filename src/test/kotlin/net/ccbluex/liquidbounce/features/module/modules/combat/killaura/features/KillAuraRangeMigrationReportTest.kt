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
package net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KillAuraRangeMigrationReportTest {

    @Test
    fun `successful legacy migration reports the existing message once`() {
        val messages = mutableListOf<String>()

        reportKillAuraRangeMigration(messages::add)

        assertEquals(listOf("KillAura Range Config migrated from old format."), messages)
    }
}
