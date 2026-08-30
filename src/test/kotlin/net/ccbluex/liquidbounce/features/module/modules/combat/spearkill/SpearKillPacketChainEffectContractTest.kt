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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SpearKillPacketChainEffectContractTest {

    @Test
    fun `packet chain state port retains neutral delegation and started early return`() {
        val state = Files.readString(STATE)
        val module = Files.readString(MODULE)
        val facade = Files.readString(FACADE)
        val termination = Files.readString(TERMINATION)

        assertTrue(
            "abstract fun tryStartPacketChainEffect(defeatedTarget: LivingEntity): PacketChainStartResult" in state,
        )
        assertInOrder(
            module,
            "override fun tryStartPacketChainEffect(defeatedTarget: LivingEntity): PacketChainStartResult",
            "SpearKillFacadeBridge.tryStartPacketChain(this, defeatedTarget)",
        )
        assertInOrder(
            facade,
            "import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.tryStartPacketChain",
            "fun tryStartPacketChain(",
            "module.tryStartPacketChain(defeatedTarget)",
        )
        assertInOrder(
            termination,
            "PacketChainStartResult.STARTED -> return",
            "clearAStarRenderPath()",
            "beginSafeExactReturn()",
        )
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private companion object {
        val ROOT: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/module/modules/combat/spearkill",
        )
        val STATE: Path = ROOT.resolve("orchestration/session/SpearKillModuleState.kt")
        val MODULE: Path = ROOT.parent.resolve("ModuleSpearKill.kt")
        val FACADE: Path = ROOT.resolve("facade/SpearKillFacadeBridge.kt")
        val TERMINATION: Path = ROOT.resolve("session/recovery/TerminatePacketFollowOperations.kt")
    }
}
