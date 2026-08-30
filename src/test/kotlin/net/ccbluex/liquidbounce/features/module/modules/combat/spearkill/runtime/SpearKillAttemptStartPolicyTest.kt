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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.delivery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.KILL_AURA_INHERITED_TARGET_SOURCE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpearKillAttemptStartPolicyTest {

    @Test
    fun `explicit target source wins over the inherited KillAura target`() {
        val plan = resolveSpearKillAttemptStartPlan(
            attemptInput(
                targetSourceOverride = "FightBot",
                inheritsKillAuraTarget = true,
            ),
        )

        assertEquals("FightBot", plan.targetSource)
    }

    @Test
    fun `the inherited KillAura target retains its canonical source`() {
        val plan = resolveSpearKillAttemptStartPlan(
            attemptInput(inheritsKillAuraTarget = true),
        )

        assertEquals(KILL_AURA_INHERITED_TARGET_SOURCE, plan.targetSource)
    }

    @Test
    fun `configured source and blank entity name retain the existing fallback plan`() {
        val plan = resolveSpearKillAttemptStartPlan(
            attemptInput(
                targetName = "  ",
                targetEntityId = 41,
                configuredTargetSource = "Crosshair",
                currentTick = 100,
                hitTicks = 3,
                chargeTicks = 7,
            ),
        )

        assertEquals("target-identity", plan.targetIdentity)
        assertEquals("entity-41", plan.targetName)
        assertEquals("Crosshair", plan.targetSource)
        assertEquals("PacketAStar", plan.plannedRouteMode)
        assertEquals(4, plan.plannedOutboundStepCount)
        assertEquals(103, plan.predictedHitTick)
        assertEquals(7, plan.chargeTicks)
        assertEquals(true, plan.terminalAuthorizationRequired)
    }

    private fun attemptInput(
        targetName: String = "Target",
        targetEntityId: Int = 7,
        targetSourceOverride: String? = null,
        inheritsKillAuraTarget: Boolean = false,
        configuredTargetSource: String = "SpearKill",
        currentTick: Int = 20,
        hitTicks: Int = 2,
        chargeTicks: Int = 5,
    ) = SpearKillAttemptStartPolicyInput(
        targetIdentity = "target-identity",
        targetName = targetName,
        targetEntityId = targetEntityId,
        targetSourceOverride = targetSourceOverride,
        inheritsKillAuraTarget = inheritsKillAuraTarget,
        configuredTargetSource = configuredTargetSource,
        routeMode = "PacketAStar",
        outboundSteps = 4,
        currentTick = currentTick,
        hitTicks = hitTicks,
        chargeTicks = chargeTicks,
        terminalAuthorizationRequired = true,
    )
}
