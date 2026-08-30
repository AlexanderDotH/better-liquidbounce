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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.ScheduleInventoryActionEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.render.murdermystery.ModuleMurderMystery
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

object ModuleAutoDodge : ClientModule("AutoDodge", ModuleCategories.COMBAT) {

    internal val mode = choices("Mode", Movement, arrayOf(Movement, Packet))
    private val ignore by multiEnumChoice("Ignore", Ignore.entries)

    private val defense = AutoDodgeDefenseRuntime()
    private val shield = AutoDodgeShieldRuntime { logger.warn(it) }

    init {
        tree(AllowRotationChange)
        tree(AllowTimer)
        tree(Spear)
        tree(Mace)
    }

    override val running: Boolean
        get() = shouldRunAutoDodgeHandlers(super.running, shield.cleanupPending)

    /** Keeps vanilla's key handler from releasing a shield use that AutoDodge currently owns. */
    @JvmStatic
    fun ownsSpearShieldUse(): Boolean = shield.ownsShieldUse()

    /** Prevents vanilla from immediately restarting the item use AutoDodge is interrupting. */
    @JvmStatic
    fun suppressesVanillaSpearShieldUse(): Boolean = shield.suppressesVanillaShieldUse()

    @Suppress("unused")
    val tickRep = handler<MovementInputEvent>(priority = SAFETY_FEATURE) { event ->
        val canStartDefense = defense.handleInput(
            event,
            enabled,
            mode.activeMode === Packet,
            runtimeContext(),
        )
        // Movement teleports can change the shield arc immediately; Packet leaves the local position untouched.
        shield.update(canStartDefense, enabled, defense.primarySpearThreat)
        AutoDodgeDiagnostics.update(defense, shield)
    }

    @Suppress("unused")
    private val packetHoldHandler = handler<PacketEvent>(priority = SAFETY_FEATURE) { event ->
        if (defense.shouldSuppressPacket(event)) event.cancelEvent()
    }

    @Suppress("unused")
    private val scheduleShieldInventoryHandler = handler<ScheduleInventoryActionEvent> { event ->
        shield.schedulePending(event)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        defense.resetAll(returnToOrigin = false)
        shield.worldReset()
    }

    @Suppress("unused")
    private val disconnectHandler = handler<DisconnectEvent> {
        defense.resetAll(returnToOrigin = false)
        shield.worldReset()
    }

    private fun runtimeContext() = AutoDodgeRuntimeContext(
        blinkActive = ModuleBlink.running,
        inventoryBlocked = Ignore.OPEN_INVENTORY !in ignore &&
            (InventoryManager.isInventoryOpen || mc.gui.screen() is ContainerScreen),
        scaffoldBlocked = Ignore.USING_SCAFFOLD !in ignore && ModuleScaffold.running,
        usingItem = player.isUsingItem,
        allowWhileUsingItem = Ignore.USING_ITEM in ignore,
        murderMysteryDisallowsProjectile = ModuleMurderMystery.disallowsArrowDodge(),
        cleanupPending = shield.cleanupPending,
    )

    internal fun resetSpearTeleport() = defense.resetSpearTeleport()

    internal fun resetMaceMovement() = defense.resetMaceMovement()

    internal fun resetMaceTeleport() = defense.resetMaceTeleport()

    internal fun enterPacketMode() = defense.enterPacketMode()

    internal fun resetPacketRuntime(returnToOrigin: Boolean = true) =
        defense.resetPacketRuntime(returnToOrigin)

    internal fun disableSpearShield() = shield.disable()

    override fun onDisabled() {
        defense.resetAll()
        shield.disable()
        super.onDisabled()
    }

    data class EvadingPacket(
        val idx: Int,
        /** Ticks until impact. Null if evaded. */
        val ticksToImpact: Int?,
    )

    /** Returns the first Blink position packet that improves or avoids the predicted arrow impact. */
    fun findAvoidingArrowPosition() = defense.findAvoidingArrowPosition()

    fun getInflictedHit(pos: Vec3) = defense.getInflictedHit(pos)

    data class HitInfo(
        val tickDelta: Int,
        val arrowEntity: Entity,
        val hitPos: Vec3,
        val prevArrowPos: Vec3,
        val arrowVelocity: Vec3,
    )
}
