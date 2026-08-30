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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.ScreenEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantPlanningStep
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantReachValue
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantRoundRobinPass
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.vanilla.model.MerchantTradeFiltersValue
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FIRST_PRIORITY
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.inventory.MerchantMenu

object AutoShopVanillaMode : Mode("Vanilla") {
    internal val tradeFilters = value(MerchantTradeFiltersValue("Trades"))
    internal val reach = value(MerchantReachValue("Reach"))
    internal val cps by intRange("CPS", 4..8, 1..20, "clicks")
    internal val rotations = tree(RotationsValueGroup(this))

    internal val session = MerchantSessionCoordinator()
    internal val cpsGate = MerchantCpsGate()
    internal val abandonedOpeningGuard = MerchantAbandonedOpeningGuard(
        MerchantSessionCoordinator.DEFAULT_TIMEOUT_TICKS,
    )
    internal val feedbackGate = MerchantTradeFeedbackGate()
    internal val planningStepCache = MerchantPlanningStepCache()
    internal var roundRobinPass: MerchantRoundRobinPass? = null
    internal var ownedMenu: MerchantMenu? = null
    internal var sendingOwnedInteraction = false
    internal var suppressAcquisitionUntilTick = Int.MIN_VALUE

    override val parent: ModeValueGroup<Mode>
        get() = ModuleAutoShop.modes

    @Suppress("unused")
    private val rotationHandler = handler<RotationUpdateEvent> {
        val localPlayer = mc.player ?: return@handler
        val currentTick = localPlayer.tickCount
        if (yieldToUserInteraction(currentTick)) return@handler
        val target = when (val current = session.state) {
            MerchantSessionState.Idle -> acquireTarget(currentTick)
            is MerchantSessionState.Rotating -> merchant(current.targetId)
            else -> null
        } ?: return@handler
        val spot = eligibleRotationSpot(target)
        if (spot == null) {
            finishSession(MerchantSessionEndCause.TARGET_LOST, currentTick)
            return@handler
        }
        requestInteractionRotation(target, spot)
    }

    @Suppress("unused")
    private val gameTickHandler = handler<GameTickEvent> {
        val localPlayer = mc.player ?: return@handler
        if (yieldToUserInteraction(localPlayer.tickCount)) return@handler
        val current = session.state
        if (current === MerchantSessionState.Idle) return@handler
        when {
            mc.gui.screen() != null ->
                finishSession(MerchantSessionEndCause.UNEXPECTED_GUI, localPlayer.tickCount)
            session.hasTimedOut(localPlayer.tickCount) ->
                finishSession(MerchantSessionEndCause.TIMEOUT, localPlayer.tickCount)
            !lockedTargetIsValid() ->
                finishSession(MerchantSessionEndCause.TARGET_LOST, localPlayer.tickCount)
            current is MerchantSessionState.AwaitingOffers -> awaitOffers(current, localPlayer.tickCount)
            current is MerchantSessionState.Trading -> trade(current, localPlayer.tickCount)
        }
    }

    @Suppress("unused")
    private val screenHandler = handler<ScreenEvent>(priority = FIRST_PRIORITY) { event ->
        val screen = event.screen
        if (screen is MerchantScreen && discardAbandonedMerchantScreen(screen)) {
            event.cancelEvent()
            return@handler
        }
        if (screen is MerchantScreen && claimOwnedScreen(screen)) {
            event.cancelEvent()
            return@handler
        }
        if (screen != null && session.state !== MerchantSessionState.Idle) {
            finishSession(MerchantSessionEndCause.UNEXPECTED_GUI, mc.player?.tickCount ?: 0)
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent>(priority = FIRST_PRIORITY) { event ->
        when (val packet = event.packet) {
            is ServerboundInteractPacket -> handleInteractionPacket(event.origin, packet)
            is ServerboundUseItemOnPacket,
            is ServerboundUseItemPacket -> handleUserInteractionPacket(event.origin)
            is ClientboundOpenScreenPacket -> handleOpenScreenPacket(event.origin, packet)
            is ClientboundMerchantOffersPacket -> handleMerchantOffersPacket(event.origin, packet)
            is ClientboundContainerClosePacket -> handleServerClose(event.origin, packet)
        }
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        endSession(MerchantSessionEndCause.WORLD_CHANGE)
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        endSession(MerchantSessionEndCause.DISCONNECT)
    }

    override fun disable() {
        endSession(MerchantSessionEndCause.DISABLE_OR_MODE_SWITCH)
    }
}
