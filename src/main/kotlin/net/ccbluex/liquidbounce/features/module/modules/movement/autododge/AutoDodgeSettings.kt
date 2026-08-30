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

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.SpearTeleportSettings
import net.ccbluex.liquidbounce.features.inventory.PlayerInventoryConstraints

internal class SpearTeleportValueGroup(
    parent: EventListener,
    private val resetRuntime: () -> Unit,
    defaultEnabled: Boolean = false,
) : ToggleableValueGroup(parent, "Teleport", defaultEnabled) {
    val behindDistance by float("BehindDistance", 2.0F, 0.5F..5.0F, suffix = "blocks")
    val maxDistance by float("MaxDistance", 12.0F, 2.0F..32.0F, suffix = "blocks")
    val searchRadius by int("SearchRadius", 2, 0..5, suffix = "blocks")
    val cooldown by int("Cooldown", 6, 0..40, suffix = "ticks")
    val stepDistance by float("StepDistance", 4.0F, 0.25F..10.0F, suffix = "blocks")
    val maxPackets by int("MaxPackets", 8, 1..32)

    fun settings() = SpearTeleportSettings(
        behindDistance = behindDistance.toDouble(),
        maxDistance = maxDistance.toDouble(),
        searchRadius = searchRadius,
        cooldownTicks = cooldown,
        stepDistance = stepDistance.toDouble(),
        maxPackets = maxPackets,
    )

    override fun onDisabled() {
        resetRuntime()
        super.onDisabled()
    }
}

internal sealed class AutoDodgeMode(name: String) : Mode(name)

internal object Movement : AutoDodgeMode("Movement") {
    override fun enable() {
        ModuleAutoDodge.resetPacketRuntime()
    }
}

internal object Packet : AutoDodgeMode("Packet") {
    val cooldown by int(
        "Cooldown",
        AUTO_DODGE_PACKET_MIN_COOLDOWN_TICKS,
        AUTO_DODGE_PACKET_MIN_COOLDOWN_TICKS..AUTO_DODGE_PACKET_MAX_COOLDOWN_TICKS,
        suffix = "ticks",
    )
    val holdTicks by int(
        "HoldTicks",
        AUTO_DODGE_PACKET_DEFAULT_HOLD_TICKS,
        AUTO_DODGE_PACKET_MIN_HOLD_TICKS..AUTO_DODGE_PACKET_MAX_HOLD_TICKS,
        suffix = "ticks",
    )

    override fun enable() {
        ModuleAutoDodge.enterPacketMode()
    }

    override fun disable() {
        ModuleAutoDodge.resetPacketRuntime()
    }
}

internal object AllowRotationChange : ToggleableValueGroup(ModuleAutoDodge, "AllowRotationChange", false) {
    val allowJump by boolean("AllowJump", true)
}

internal object AllowTimer : ToggleableValueGroup(ModuleAutoDodge, "AllowTimer", false) {
    val timerSpeed by float("TimerSpeed", 2.0F, 1.0F..10.0F, suffix = "x")
}

internal object Spear : ToggleableValueGroup(ModuleAutoDodge, "Spear", false) {
    val aimMargin by float("AimMargin", 0.75F, 0.0F..3.0F, suffix = "blocks")
    val visibilityGrace by int("VisibilityGrace", 8, 0..40, suffix = "ticks")
    val jukeTicks by intRange("JukeTicks", 2..5, 1..10, suffix = "ticks")
    val threatMemory by int("ThreatMemory", 5, 0..20, suffix = "ticks")
    val teleport = SpearTeleportValueGroup(this, ModuleAutoDodge::resetSpearTeleport)

    fun movementSettings() = SpearMovementSettings(
        enabled = enabled,
        aimMargin = aimMargin.toDouble(),
        visibilityGraceTicks = visibilityGrace,
        jukeTicks = jukeTicks,
        threatMemoryTicks = threatMemory,
        teleportEnabled = teleport.enabled,
        teleport = teleport.settings(),
    )

    object Shield : ToggleableValueGroup(Spear, "Shield", true) {
        val releaseDelay by int("ReleaseDelay", 3, 0..20, suffix = "ticks")
        val constraints = tree(
            PlayerInventoryConstraints(
                startDelayDefault = 0..0,
                clickDelayDefault = 0..0,
                closeDelayDefault = 0..0,
                missChanceDefault = 0..0,
            )
        )

        override fun onDisabled() {
            ModuleAutoDodge.disableSpearShield()
            super.onDisabled()
        }
    }

    init {
        tree(teleport)
        tree(Shield)
    }
}

internal object Mace : ToggleableValueGroup(ModuleAutoDodge, "Mace", true) {
    val packetThreatRange by float("PacketThreatRange", 512.0F, 16.0F..512.0F, suffix = "blocks")
    val threatMemory by int("ThreatMemory", 5, 0..20, suffix = "ticks")
    val teleport = SpearTeleportValueGroup(
        this,
        ModuleAutoDodge::resetMaceTeleport,
        defaultEnabled = true,
    )

    init {
        tree(teleport)
    }

    fun movementSettings() = MaceMovementSettings(
        enabled = enabled,
        packetThreatRange = packetThreatRange.toDouble(),
        threatMemoryTicks = threatMemory,
        teleportEnabled = teleport.enabled,
        teleport = teleport.settings(),
    )

    override fun onDisabled() {
        ModuleAutoDodge.resetMaceMovement()
        ModuleAutoDodge.resetMaceTeleport()
        super.onDisabled()
    }
}

internal enum class Ignore(
    override val tag: String
) : Tagged {
    OPEN_INVENTORY("OpenInventory"),
    USING_ITEM("UsingItem"),
    USING_SCAFFOLD("UsingScaffold")
}
