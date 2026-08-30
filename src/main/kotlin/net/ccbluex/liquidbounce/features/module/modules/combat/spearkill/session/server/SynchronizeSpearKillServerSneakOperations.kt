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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.shouldMaintainSpearKillServerSneak

import net.ccbluex.liquidbounce.utils.client.isNewerThanOrEquals1_21_6
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.usesViaFabricPlus
import net.ccbluex.liquidbounce.utils.network.sendLegacyStartSneaking
import net.ccbluex.liquidbounce.utils.network.sendLegacyStopSneaking
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input

/**
 * Brackets a Packet route with server-visible sneaking without changing the local input or
 * rendering pose. The start packet is emitted before the first movement packet; later input
 * packets are forced to retain it until the route has physically returned or aborts.
 */
internal fun SpearKillModuleState.synchronizeSpearKillServerSneak() {
    if (mc.player == null || mc.level == null) {
        serverSneaking = false
        return
    }

    when (SpearKillServerSneak.nextAction(serverSneaking, shouldMaintainSpearKillServerSneak)) {
        SpearKillServerSneak.Action.START -> {
            serverSneaking = true
            sendSpearKillServerSneakInput(forceSneak = true)
        }

        SpearKillServerSneak.Action.STOP -> {
            serverSneaking = false
            sendSpearKillServerSneakInput(forceSneak = false)
        }

        SpearKillServerSneak.Action.NONE -> Unit
    }
}

internal fun SpearKillModuleState.sendSpearKillServerSneakInput(forceSneak: Boolean) {
    val input = player.input.keyPresses
    if (usesViaFabricPlus && !isNewerThanOrEquals1_21_6) {
        if (forceSneak) {
            network.sendLegacyStartSneaking()
        } else if (!input.shift) {
            network.sendLegacyStopSneaking()
        }
        return
    }

    network.send(
        ServerboundPlayerInputPacket(
            Input(
                input.forward,
                input.backward,
                input.left,
                input.right,
                input.jump,
                forceSneak || input.shift,
                input.sprint,
            ),
        ),
    )
}
