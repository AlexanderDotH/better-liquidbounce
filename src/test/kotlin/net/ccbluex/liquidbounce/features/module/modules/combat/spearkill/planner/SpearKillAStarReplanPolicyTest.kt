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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpearKillAStarReplanPolicyTest {

    @Test
    fun `missing prediction inputs fail without evaluating damage window`() {
        var damageWindowChecks = 0
        val checkDamageWindow = { _: String, _: Int ->
            damageWindowChecks++
            true
        }

        assertNull(selectUsableSpearKillAStarReplan(null, 10, checkDamageWindow))
        assertNull(selectUsableSpearKillAStarReplan("plan", null, checkDamageWindow))
        assertEquals(0, damageWindowChecks)
    }

    @Test
    fun `invalid damage window rejects an otherwise complete prediction`() {
        assertNull(selectUsableSpearKillAStarReplan("plan", 10) { _, _ -> false })
    }

    @Test
    fun `valid damage window retains exact prediction identity`() {
        val plan = Any()

        assertEquals(plan, selectUsableSpearKillAStarReplan(plan, 10) { candidate, duration ->
            candidate === plan && duration == 10
        })
    }
}
