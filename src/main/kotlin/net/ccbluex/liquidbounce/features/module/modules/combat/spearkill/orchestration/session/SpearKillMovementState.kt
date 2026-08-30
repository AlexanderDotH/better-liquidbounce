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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillMovementAssistMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillServerSneak
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.calculateSpearKillVanillaMovementBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.resolveSpearKillMovementAssistLease
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.features.input.InputTracker.wasPressedRecently
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.Items

internal val SpearKillModuleState.shouldRaiseSpearAnimation
    get() = shouldRaiseSpearKillAnimation(
        spearKillRunning = running,
        holdingSpear = holdingSpear,
        attackPathActive = hasActiveAttackPath,
        attackRequested = isSpearUseRequested,
        isUsingSpear = isUsingSpear,
    )
internal val SpearKillModuleState.usesPacketMovementMode get() = movement.activeMode === movementConfiguration.packet
internal val SpearKillModuleState.packetRoutingMode
    get() = when (movementConfiguration.packet.routing.activeMode) {
        movementConfiguration.packet.aStar -> SpearKillRoutingMode.A_STAR
        movementConfiguration.packet.networkOptimized -> SpearKillRoutingMode.NETWORK_OPTIMIZED
        movementConfiguration.packet.instant -> SpearKillRoutingMode.INSTANT
        else -> SpearKillRoutingMode.DIRECT
    }
internal val SpearKillModuleState.activePacketRoutingMode
    get() = packetSessionSettings?.routingMode ?: packetRoutingMode
internal val SpearKillModuleState.usesNetworkOptimizedRouting
    get() = usesPacketMovementMode && packetRoutingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED
internal val SpearKillModuleState.packetRoutingSupportsAStar
    get() = usesPacketMovementMode &&
        (activePacketRoutingMode == SpearKillRoutingMode.A_STAR ||
            activePacketRoutingMode == SpearKillRoutingMode.NETWORK_OPTIMIZED)
internal val SpearKillModuleState.configuredPrimedInstant
    get() = usesPacketMovementMode && packetRoutingMode == SpearKillRoutingMode.INSTANT &&
        movementConfiguration.packet.instant.strategy.activeMode ===
        movementConfiguration.packet.instant.primed
internal val SpearKillModuleState.activePrimedInstant
    get() = packetSessionSettings?.primedInstant ?: configuredPrimedInstant
internal val SpearKillModuleState.activeInstantEndpointOnly
    get() = packetSessionSettings?.let { settings ->
        usesSpearKillPrimedEndpointOnlyPreflight(settings.primedInstant, settings.priming)
    } == true
internal val SpearKillModuleState.packetRoutingAllowsOccludedTarget
    get() = packetRoutingSupportsAStar || configuredPrimedInstant
internal val SpearKillModuleState.activePacketStepWaitTicks
    get() = packetSessionSettings?.stepWaitTicks ?: movementConfiguration.packet.stepDelay
internal val SpearKillModuleState.activeStepLimit
    get() = if (usesPacketMovementMode) {
        movementConfiguration.packet.stepDistance
    } else {
        movementConfiguration.motion.stepDistance
    }
internal val SpearKillModuleState.activeSpeedStepDistance
    get() = activeMovementTransport?.stepLimit ?: activeStepLimit.toDouble()

internal val SpearKillModuleState.currentVanillaMovementBudget: Double
    get() = calculateSpearKillVanillaMovementBudget(
        serverPhysicsVelocity = player.deltaMovement,
        fallFlying = player.isFallFlying,
    )

internal val SpearKillModuleState.hasUsableSpearKillElytra
    get() = player.getItemBySlot(EquipmentSlot.CHEST).let { chestItem ->
        chestItem.`is`(Items.ELYTRA) && !chestItem.nextDamageWillBreak()
    }

internal val SpearKillModuleState.isSpearKillElytraActive
    get() = elytraWhileMoving != SpearKillMovementAssistMode.NONE &&
        hasUsableSpearKillElytra && player.isFallFlying

internal val SpearKillModuleState.shouldProtectFallDamage
    get() = shouldProtectSpearKillFallDamage(
        fallDistance = player.fallDistance.toDouble(),
        verticalVelocity = player.deltaMovement.y,
        safeFallDistance = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
        tickCount = player.tickCount,
    )
internal val SpearKillModuleState.safeVirtualFallStep
    get() = spearKillSafeVirtualVerticalStep(
        player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),
    )

internal val SpearKillModuleState.isUsingSpear get() = player.isUsingItem && player.useItem.isSpear
internal val SpearKillModuleState.holdingSpear get() = player.mainHandItem.isSpear || player.offhandItem.isSpear
internal val SpearKillModuleState.isUseInputHeld get() = mc.options.keyUse.isPressedOnAny
internal val SpearKillModuleState.hasFightBotSpearRequest
    get() = fightBotSpearState == SpearKillFightBotState.Charging ||
        fightBotSpearState == SpearKillFightBotState.RouteActive
internal val SpearKillModuleState.hasKillAuraSpearRequest get() = killAuraSpearTarget != null
internal val SpearKillModuleState.hasKillAuraSpearUseRequest
    get() = hasKillAuraSpearRequest || killAuraSpearPrechargeActive
internal val SpearKillModuleState.hasAutomaticSpearRequest
    get() = hasFightBotSpearRequest || hasKillAuraSpearRequest
internal val SpearKillModuleState.isSpearUseRequested
    get() = isUseInputHeld || hasFightBotSpearRequest || hasKillAuraSpearUseRequest
internal val SpearKillModuleState.isAttackInputHeld get() = mc.options.keyAttack.isPressedOnAny
internal val SpearKillModuleState.movementAssistLease
    get() = resolveSpearKillMovementAssistLease(
        preparationActive = movementAssistPreparationActive,
        routeActive = hasActiveAttackPath,
        sneakMode = sneakWhileMoving,
        elytraMode = elytraWhileMoving,
        elytraUsable = hasUsableSpearKillElytra,
        elytraActive = activeMovementTransport?.elytraActive ?: isSpearKillElytraActive,
    )
internal val SpearKillModuleState.shouldMaintainSpearKillServerSneak
    get() = SpearKillServerSneak.shouldMaintain(
        requestedByRoute = movementAssistLease.serverSneak,
        serverSneaking = serverSneaking,
        isFallFlying = player.isFallFlying,
        currentHeight = player.boundingBox.ysize,
        crouchingHeight = player.getDimensions(Pose.CROUCHING).height.toDouble(),
    )
internal val SpearKillModuleState.physicalAttackRequest
    get() = isSpearKillAttackRequested(
        attackKeyDown = isAttackInputHeld,
        attackPressedRecently = mc.options.keyAttack.wasPressedRecently(SPEAR_KILL_ATTACK_REQUEST_WINDOW_MS),
    )
internal val SpearKillModuleState.hasAttackRequest get() = manualAttackRequestLatched || physicalAttackRequest
internal val SpearKillModuleState.hasActivationRequest
    get() = hasFightBotSpearRequest || isSpearKillActivationSatisfied(
        activationMode = activationMode,
        attackRequested = hasAttackRequest,
        useKeyDown = isUseInputHeld,
        inheritedKillAuraRequest = hasKillAuraSpearRequest,
    )
/** Keeps vanilla from releasing the continuous spear use started for KillAura inheritance. */
