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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.abortSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.releaseStandaloneRemoteMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetSpearKillSpeedSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.resetAttack() {
    val motionAttemptActive = attackMovements.isNotEmpty()
    val retainAStarRenderPath = packetAStarAttackActive && packetBootSession.active
    previewTarget = null
    if (!retainAStarRenderPath) {
        packetAStarAttackActive = false
        clearAStarRenderPath()
        clearAStarTargetLock()
    }
    if (attackMovements.isNotEmpty()) player.deltaMovement = Vec3.ZERO
    attackMovements.clear()
    movementAssistPreparationActive = false
    if (motionAttemptActive) {
        abortSpearKillAttempt("motion-reset")
        resetSpearKillSpeedSession()
        releaseStandaloneRemoteMovementOwnership()
    }
    motionPacketHeading = null
    fallDamageDeliveryTracker.clear()
    beginSafeExactReturn()
    applyConfirmedPhysicalReturnPosition()
    if (!packetBootSession.active) {
        packetSessionSettings = null
        activeMovementTransport = null
    }
    synchronizeSpearKillServerSneak()
}
