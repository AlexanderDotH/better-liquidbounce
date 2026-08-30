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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPort
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.SpearKillPreview
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillSetbackCallbacks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.SpearKillSetbackHook
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.activeRouteHeading
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.computedAttackDirection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.computedAttackVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.computedControlsSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.currentPreviewGlow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.shouldAnimateSpearKillUseItem
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.shouldPreserveSpearKillInheritedUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.shouldRaiseSpearAnimation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.spearKillAnimationTicks
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.spearKillRaisedHand
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.acceptsKillAuraTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.clearFacadeAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.finishFacadeSetbackCorrection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.prepareFacadeSetbackCorrection
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerDisconnectHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerFallDamagePacketHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerMovementInputHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerNetworkMovementHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.registerPacketDeliveryHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerPacketSafetyHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerRenderHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerRouteRotationHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerServerSneakPacketHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerTickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.registerWorldChangeHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.releaseFightBotUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.requestFightBotUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.reservesFightBotUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.resolveFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.startExplicitHighSpeedResearchProbe
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.tryStartPacketChain
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal object SpearKillFacadeBridge {

    internal val newPacketSessionPort: SpearKillPacketSessionPort
        get() = SpearKillPacketSessionPortAdapter()

    fun initializePreview(module: SpearKillModuleState) {
        module.tree(SpearKillPreview.bind(module))
        TargetGlowSourceRegistry.register(module::currentPreviewGlow)
    }

    fun currentAttackVelocity(module: SpearKillModuleState) = module.computedAttackVelocity

    fun currentAttackDirection(module: SpearKillModuleState) = module.computedAttackDirection

    fun controlsSpearUse(module: SpearKillModuleState) = module.computedControlsSpearUse

    fun prepareSetbackCorrection(
        module: SpearKillModuleState,
        packet: ClientboundPlayerPositionPacket,
        player: Player,
    ) = module.prepareFacadeSetbackCorrection(packet, player)

    fun finishSetbackCorrection(
        module: SpearKillModuleState,
        packet: ClientboundPlayerPositionPacket,
        player: Player,
    ) = module.finishFacadeSetbackCorrection(packet, player)

    fun clearAttack(module: SpearKillModuleState, reason: String) = module.clearFacadeAttack(reason)

    fun fightBotStateFor(module: SpearKillModuleState, target: LivingEntity): SpearKillFightBotState =
        module.resolveFightBotState(target)

    fun reservesFightBotSpearUse(module: SpearKillModuleState, target: LivingEntity?): Boolean =
        module.reservesFightBotUse(target)

    fun requestFightBotSpearUse(module: SpearKillModuleState, target: LivingEntity): SpearKillFightBotState =
        module.requestFightBotUse(target)

    fun releaseFightBotSpearUse(module: SpearKillModuleState, terminal: SpearKillFightBotTerminal) {
        module.releaseFightBotUse(terminal)
    }

    fun tryStartPacketChain(
        module: SpearKillModuleState,
        defeatedTarget: LivingEntity,
    ): PacketChainStartResult = module.tryStartPacketChain(defeatedTarget)

    fun canAcceptKillAuraTarget(module: SpearKillModuleState, target: LivingEntity): Boolean =
        module.acceptsKillAuraTarget(target)

    fun startHighSpeedResearchProbe(
        module: SpearKillModuleState,
        request: SpearKillHighSpeedResearchProbeRequest,
    ): SpearKillHighSpeedResearchProbeStartResult = module.startExplicitHighSpeedResearchProbe(request)

    fun routeRotationOverride(module: SpearKillModuleState): Rotation? =
        module.activeRouteHeading.takeIf { module.running }

    fun controlsSpearAnimation(module: SpearKillModuleState): Boolean = module.shouldRaiseSpearAnimation

    fun ownsKillAuraSpearUse(module: SpearKillModuleState): Boolean {
        val currentPlayer = module.mc.player ?: return false
        return shouldPreserveSpearKillInheritedUse(
            startedUse = module.killAuraStartedSpearUse,
            isUsingItem = currentPlayer.isUsingItem,
            isSameHand = module.killAuraSpearUseHand == currentPlayer.usedItemHand,
            isUsingSpear = currentPlayer.useItem.isSpear,
        )
    }

    fun shouldAnimateRaisedSpear(module: SpearKillModuleState): Boolean = shouldAnimateSpearKillUseItem(
        shouldRaise = module.shouldRaiseSpearAnimation,
        isUsingItem = module.player.isUsingItem,
    )

    fun raisedSpearHand(module: SpearKillModuleState): InteractionHand? = spearKillRaisedHand(
        shouldRaise = module.shouldRaiseSpearAnimation,
        mainHandIsSpear = module.player.mainHandItem.isSpear,
        offHandIsSpear = module.player.offhandItem.isSpear,
        isUsingItem = module.player.isUsingItem,
        usedHand = module.player.usedItemHand,
    )

    fun getSpearAnimationTicks(module: SpearKillModuleState, hand: InteractionHand, originalTicks: Float): Float {
        if (!module.shouldRaiseSpearAnimation || raisedSpearHand(module) != hand) return originalTicks
        val spearStack = when (hand) {
            InteractionHand.MAIN_HAND -> module.player.mainHandItem
            InteractionHand.OFF_HAND -> module.player.offhandItem
        }
        val delayTicks = spearStack.get(DataComponents.KINETIC_WEAPON)?.delayTicks ?: return originalTicks
        return spearKillAnimationTicks(true, delayTicks, originalTicks)
    }

    fun getSpearAnimationTicks(module: SpearKillModuleState, entity: LivingEntity, originalTicks: Float): Float =
        if (entity === module.player) {
            getSpearAnimationTicks(module, raisedSpearHand(module) ?: module.player.usedItemHand, originalTicks)
        } else {
            originalTicks
        }

    fun registerHandlers(module: SpearKillModuleState) = with(module) {
        SpearKillSetbackHook.install(SpearKillSetbackCallbacks(
            beforeCorrection = module::prepareFacadeSetbackCorrection,
            afterCorrection = module::finishFacadeSetbackCorrection,
        ))
        registerRouteRotationHandler()
        registerMovementInputHandler()
        registerTickHandler()
        registerNetworkMovementHandler()
        registerServerSneakPacketHandler()
        registerPacketSafetyHandler()
        registerFallDamagePacketHandler()
        registerPacketDeliveryHandler()
        registerWorldChangeHandler()
        registerDisconnectHandler()
        registerRenderHandler()
    }

    fun disable(module: SpearKillModuleState) = with(module) {
        failureNotificationGate.clear()
        networkOptimizer.reset()
        holdUseLaunchTarget = null
        module.clearFacadeAttack("disabled")
        rejectedTargets.clear()
        highSpeedResearch.close()
        if (debugConsole.isInitialized()) debugConsole.value.clearTransitions()
    }
}
