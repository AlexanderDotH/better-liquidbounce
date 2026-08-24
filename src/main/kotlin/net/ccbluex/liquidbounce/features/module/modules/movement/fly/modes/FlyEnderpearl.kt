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

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationCapabilities
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationKind
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation.FlyAutomationReadiness
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleFastUse
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.isBlockAtPosition
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.box
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block

internal object FlyEnderpearl : Mode("Enderpearl"), FlyAutomationProfile {

    override val parent: ModeValueGroup<*>
        get() = ModuleFly.modes

    private val speed by float("Speed", 1f, 0.5f..2f)

    private var threwPearl = false
    private var shouldFly = false

    override val automationCapabilities = FlyAutomationCapabilities(
        horizontal = true,
        ascend = true,
        descend = true,
        landing = true,
        kind = FlyAutomationKind.CONTINUOUS,
        resource = "Ender Pearl",
    )

    override fun automationReadiness(): FlyAutomationReadiness = when {
        shouldFly -> FlyAutomationReadiness.Ready
        threwPearl -> FlyAutomationReadiness.Arming("Waiting for the ender pearl teleport")
        Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) == null ->
            FlyAutomationReadiness.Unavailable("No ender pearl is available")
        else -> FlyAutomationReadiness.Arming("Preparing the ender pearl")
    }

    private val rotations = tree(RotationsValueGroup(this))

    override fun enable() {
        threwPearl = false
        shouldFly = false
    }

    override fun disable() {
        SilentHotbar.resetSlot(this)
        threwPearl = false
        shouldFly = false
    }

    val repeatable = tickHandler {
        if (player.isDeadOrDying || player.isSpectator || player.abilities.instabuild) {
            return@tickHandler
        }

        if (shouldFly) { // Fly after setback/pearl land
            player.deltaMovement = player.deltaMovement.withFlyAutomationStrafe(player, speed.toDouble())

            player.deltaMovement.y = when {
                flyAutomationJump(mc.options.keyJump.isDown) -> speed.toDouble()
                flyAutomationSneak(mc.options.keyShift.isDown) -> -speed.toDouble()
                else -> 0.0
            }

            return@tickHandler
        }

        if (threwPearl) return@tickHandler // Already threw pearl, nothing to do

        // If there isn't a pearl, return
        val slot = Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) ?: return@tickHandler

        if (player.xRot <= 80) {
            RotationManager.setRotationTarget(
                Rotation(flyAutomationYaw(player.yRot), (80f..90f).random()),
                valueGroup = rotations,
                provider = ModuleFastUse,
                priority = Priority.IMPORTANT_FOR_USAGE_2
            )
        }

        waitTicks(2)
        SilentHotbar.selectSlotSilently(this, slot, 1)
        interaction.startPrediction(world) { sequence ->
            ServerboundUseItemPacket(slot.useHand, sequence, flyAutomationYaw(player.yRot), player.xRot)
        }

        threwPearl = true
    }

    val packetHandler = handler<PacketEvent> { event ->
        if (event.origin == TransferOrigin.OUTGOING && event.packet is ServerboundAcceptTeleportationPacket
            && isABitAboveGround() && threwPearl) { // Pearl landed, accepting teleport -> should fly
            shouldFly = true
        }
    }

    fun isABitAboveGround(): Boolean {
        for (y in 0..5) {
            val boundingBox = player.box
            val detectionBox = boundingBox.setMinY(boundingBox.minY - y)

            if (detectionBox.isBlockAtPosition { it is Block }) return true
        }
        return false
    }
}
