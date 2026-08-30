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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill

import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleMaceKill

import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillAuthoritativeOriginCorrectionAbortsActiveRouteTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `authoritative origin correction aborts an active route but confirms a completed return`() {
        assertEquals(
            MaceKillOriginCorrectionAction.ABORT_ACTIVE_ROUTE,
            maceKillOriginCorrectionAction(routeSessionActive = true),
        )
        assertEquals(
            MaceKillOriginCorrectionAction.CONFIRM_COMPLETED_RETURN,
            maceKillOriginCorrectionAction(routeSessionActive = false),
        )
    }

    @Test
    fun `ordinary packet route preserves local movement while research can still pin its origin`() {
        val origin = Vec3(4.0, 70.0, -8.0)

        assertNull(
            requiredMaceKillLocalRestore(
                packetRouteOwned = true,
                preservePhysicalMovement = true,
                origin = origin,
                currentPosition = origin.add(0.25, 0.0, 0.0),
            ),
        )
        assertEquals(
            origin,
            requiredMaceKillLocalRestore(
                packetRouteOwned = true,
                preservePhysicalMovement = false,
                origin = origin,
                currentPosition = origin.add(0.25, 0.0, 0.0),
            ),
        )
        assertNull(requiredMaceKillLocalRestore(true, false, origin, origin))
        assertNull(requiredMaceKillLocalRestore(false, false, origin, origin.add(1.0, 0.0, 0.0)))
    }

    @Test
    fun `physical movement packets keep the confirmed virtual route position`() {
        val committedOffset = Vec3(8.0, 3.0, -2.0)

        assertEquals(
            committedOffset,
            maceKillPhysicalMovementVirtualOffset(
                routeOwned = true,
                packetMovement = true,
                researchActive = false,
                committedOffset = committedOffset,
            ),
        )
        assertNull(maceKillPhysicalMovementVirtualOffset(true, true, true, committedOffset))
        assertNull(maceKillPhysicalMovementVirtualOffset(true, false, false, committedOffset))
        assertNull(maceKillPhysicalMovementVirtualOffset(false, true, false, committedOffset))
    }
}
