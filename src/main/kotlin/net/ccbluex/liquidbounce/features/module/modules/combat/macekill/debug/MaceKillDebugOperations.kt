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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.debugMaceKill(event: String, fields: () -> List<Pair<String, Any?>>) {
    if (!integration.debugRunning) {
        if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
        return
    }
    debugConsole.value.log(event, fields)
}

internal fun MaceKillModuleState.debugMaceKillChanged(
    channel: String,
    event: String,
    fingerprint: () -> Any?,
    fields: () -> List<Pair<String, Any?>>,
) {
    if (!integration.debugRunning) {
        if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
        return
    }
    debugConsole.value.logChanged(channel, event, fingerprint, fields)
}

internal fun MaceKillModuleState.notifyMaceFailure(key: String) {
    if (!failureNotificationGate.shouldNotify(player.tickCount)) return
    notification(name, message(key), NotificationEvent.Severity.ERROR)
}

internal fun MaceKillModuleState.routePositions(origin: Vec3, movements: List<Vec3>): List<Vec3> =
    buildMaceKillRoutePositions(origin, movements)

internal fun buildMaceKillRoutePositions(origin: Vec3, movements: List<Vec3>): List<Vec3> = buildList {
    var position = origin
    add(position)
    movements.forEach { movement ->
        position = position.add(movement)
        add(position)
    }
}
