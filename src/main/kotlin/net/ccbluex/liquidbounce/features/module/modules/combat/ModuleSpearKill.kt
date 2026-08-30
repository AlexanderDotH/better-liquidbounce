/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotSpearAutomation
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade.SpearKillFacadeBridge
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade.onKillAuraDisabled as facadeOnKillAuraDisabled
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.migrateLegacySpearKillConfig
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchProbeStartResult
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * Spear kill module
 *
 * Automatically attacks enemies using a charged spear.
 * Direct routes are preferred; the optional AStar route falls back around collision-blocked paths.
 */
object ModuleSpearKill : SpearKillModuleState(SpearKillFacadeBridge.newPacketSessionPort) {

    internal override val killAuraRunning: Boolean
        get() = ModuleKillAura.running

    internal override val debugEnabled: Boolean
        get() = ModuleDebug.running

    internal override val fightBotSpearAutomation: FightBotSpearAutomation
        get() = ModuleFightBot.configuredSpearAutomation

    internal override fun delegatedKillAuraTarget(): LivingEntity? = ModuleKillAura.targetForSpearKill()

    internal override fun shouldPrechargeDelegatedKillAura(): Boolean = ModuleKillAura.shouldPrechargeForSpearKill()

    internal override fun stopDelegatedKillAuraBlocking(playerUsingItem: Boolean) {
        if (playerUsingItem && KillAuraAutoBlock.enforcedBlockingHand != null) {
            KillAuraAutoBlock.stopBlocking(pauses = true)
        }
    }

    internal override fun clearFightBotSpearUseEffect(terminal: SpearKillFightBotTerminal) =
        SpearKillFacadeBridge.releaseFightBotSpearUse(this, terminal)

    internal override fun tryStartPacketChainEffect(defeatedTarget: LivingEntity): PacketChainStartResult =
        SpearKillFacadeBridge.tryStartPacketChain(this, defeatedTarget)

    internal fun onKillAuraDisabled() = facadeOnKillAuraDisabled()

    internal fun prepareSpearKillSetbackCorrection(packet: ClientboundPlayerPositionPacket, player: Player) =
        SpearKillFacadeBridge.prepareSetbackCorrection(this, packet, player)

    internal fun finishSpearKillSetbackCorrection(packet: ClientboundPlayerPositionPacket, player: Player) =
        SpearKillFacadeBridge.finishSetbackCorrection(this, packet, player)

    internal fun clearSpearKillAttack(reason: String) = SpearKillFacadeBridge.clearAttack(this, reason)

    init {
        SpearKillFacadeBridge.initializePreview(this)
    }

    internal val currentAttackVelocity get() = SpearKillFacadeBridge.currentAttackVelocity(this)
    internal val currentAttackDirection get() = SpearKillFacadeBridge.currentAttackDirection(this)
    internal val controlsSpearUse get() = SpearKillFacadeBridge.controlsSpearUse(this)

    internal fun fightBotStateFor(target: LivingEntity): SpearKillFightBotState =
        SpearKillFacadeBridge.fightBotStateFor(this, target)

    internal fun reservesFightBotSpearUse(target: LivingEntity?): Boolean =
        SpearKillFacadeBridge.reservesFightBotSpearUse(this, target)

    /** Starts or maintains a scoped use/slot reservation for FightBot's current distant target. */
    internal fun requestFightBotSpearUse(target: LivingEntity): SpearKillFightBotState =
        SpearKillFacadeBridge.requestFightBotSpearUse(this, target)

    internal fun releaseFightBotSpearUse(
        terminal: SpearKillFightBotTerminal = SpearKillFightBotTerminal.TargetLoss,
    ) = SpearKillFacadeBridge.releaseFightBotSpearUse(this, terminal)

    internal fun canAcceptKillAuraTarget(target: LivingEntity): Boolean =
        SpearKillFacadeBridge.canAcceptKillAuraTarget(this, target)

    /** Starts exactly one explicitly requested Primed probe; no matrix or retry is implicit. */
    internal fun startHighSpeedResearchProbe(
        request: SpearKillHighSpeedResearchProbeRequest,
    ): SpearKillHighSpeedResearchProbeStartResult =
        SpearKillFacadeBridge.startHighSpeedResearchProbe(this, request)

    /**
     * Exact route heading used by packets that carry their own rotation, such as use-item.
     */
    @JvmStatic
    fun routeRotationOverride(): Rotation? = SpearKillFacadeBridge.routeRotationOverride(this)

    /** True while SpearKill drives the local raised-spear pose instead of FastUse. */
    @JvmStatic
    val controlsSpearAnimation: Boolean
        get() = SpearKillFacadeBridge.controlsSpearAnimation(this)
    @JvmStatic
    fun ownsKillAuraSpearUse(): Boolean = SpearKillFacadeBridge.ownsKillAuraSpearUse(this)

    /** Forces the first-person item-use pose when SpearKill wants the spear raised. */
    @JvmStatic
    fun shouldAnimateRaisedSpear(): Boolean = SpearKillFacadeBridge.shouldAnimateRaisedSpear(this)

    /** Hand that should render the SpearKill raise pose. */
    @JvmStatic
    fun raisedSpearHand(): InteractionHand? = SpearKillFacadeBridge.raisedSpearHand(this)

    /**
     * Client-only charged spear pose. Leaves the server use duration untouched; FastUse still owns
     * non-SpearKill spear visuals.
     */
    @JvmStatic
    fun getSpearAnimationTicks(hand: InteractionHand, originalTicks: Float): Float =
        SpearKillFacadeBridge.getSpearAnimationTicks(this, hand, originalTicks)

    @JvmStatic
    fun getSpearAnimationTicks(entity: LivingEntity, originalTicks: Float): Float =
        SpearKillFacadeBridge.getSpearAnimationTicks(this, entity, originalTicks)

    init {
        SpearKillFacadeBridge.registerHandlers(this)
    }

    override val running: Boolean
        get() = super.running || packetBootSession.active || fallSafetyLifecycle.active ||
            setbackGuard.armed || setbackRollback.confirming || killAuraReturnActive

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacySpearKillConfig(jsonObject)
    }

    override fun onDisabled() {
        SpearKillFacadeBridge.disable(this)
        super.onDisabled()
    }
}

/** Extra distance around an entity's vanilla/Hitbox pick box that still counts as a crosshair selection. */
