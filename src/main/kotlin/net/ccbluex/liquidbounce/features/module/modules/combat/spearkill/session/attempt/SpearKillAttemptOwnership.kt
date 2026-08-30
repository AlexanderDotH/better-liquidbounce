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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.KILL_AURA_INHERITED_TARGET_SOURCE


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.global.GlobalSettingsCombat
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal val SpearKillModuleState.computedAttackVelocity get() = if (packetBootSession.active) 0.0 else currentMovement.length()
internal val SpearKillModuleState.computedAttackDirection get() = currentMovement.normalize()
internal val SpearKillModuleState.usesPacketMovement get() = packetBootSession.active
internal val SpearKillModuleState.currentMovement get() = attackMovements.firstOrNull() ?: Vec3.ZERO
internal val SpearKillModuleState.hasActiveAttackPath get() = attackMovements.isNotEmpty() || packetBootSession.active
internal val SpearKillModuleState.hasSpearKillReturnWork
    get() = hasActiveAttackPath || setbackGuard.armed || setbackRollback.confirming ||
        packetSetbackRecoveryAttempted
internal val SpearKillModuleState.activeRouteHeading: Rotation?
    get() = when {
        packetBootSession.active -> packetBootSession.state.pathHeading
        attackMovements.isNotEmpty() -> spearKillKineticHeading(currentMovement)
        else -> null
    }
internal val SpearKillModuleState.computedControlsSpearUse
    get() = shouldControlSpearKillUse(
        spearKillRunning = running,
        attackPathActive = hasActiveAttackPath,
        routePreparationActive = packetRoutePreparationActive,
        physicalUseRequested = isUseInputHeld,
        automaticUseRequested = hasFightBotSpearRequest || hasKillAuraSpearUseRequest,
    )
internal val SpearKillModuleState.maximumTargetRange get() = maxTargetDistance
internal val SpearKillModuleState.currentAttemptSnapshot get() = attemptTracker.current
internal val SpearKillModuleState.lastAttemptSnapshot get() = attemptTracker.lastCompleted
internal val SpearKillModuleState.fightBotRouteTarget: LivingEntity?
    get() = fightBotSpearTarget.takeIf {
        fightBotSpearState == SpearKillFightBotState.RouteActive && hasActiveAttackPath
    }
internal val SpearKillModuleState.killAuraOwnsAttempt
    get() = attemptTracker.current?.targetSource == KILL_AURA_INHERITED_TARGET_SOURCE
internal val SpearKillModuleState.ownsKillAuraRoute
    get() = killAuraOwnsAttempt &&
        (hasActiveAttackPath || setbackRollback.confirming || packetSetbackRecoveryAttempted)

internal val SpearKillModuleState.acceptsKillAuraDelegation: Boolean
    get() = GlobalSettingsCombat.delegateKillAuraAttacks && killAuraRunning

internal val SpearKillModuleState.isKillAuraIntegrationAvailable: Boolean
    get() = isSpearKillKillAuraAcquisitionAvailable(
        moduleEnabled = enabled,
        moduleRunning = running,
        delegationEnabled = acceptsKillAuraDelegation,
        holdingSpear = holdingSpear,
        routeBlocked = packetBootSession.recovering || setbackRollback.confirming ||
            packetSetbackRecoveryAttempted || hasActiveAttackPath && !ownsKillAuraRoute,
    )

internal val SpearKillModuleState.isKillAuraIntegrationArmed: Boolean
    get() = isSpearKillKillAuraAttackArmed(
        acquisitionAvailable = isKillAuraIntegrationAvailable,
        usingSpear = isUsingSpear,
        activationRequested = hasActivationRequest,
        hasKineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) != null,
    )
