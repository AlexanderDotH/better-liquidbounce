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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationBridge
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.function.BooleanSupplier

internal sealed class CrystalDestroyRotationMode(
    name: String,
    private val modeValueGroup: ModeValueGroup<CrystalDestroyRotationMode>,
    val owner: EventListener,
) : Mode(name) {

    val postMove by boolean("PostMove", false)
    val instant by boolean("Instant", false)

    abstract fun rotate(rotation: Rotation, isFinished: BooleanSupplier, onFinished: Runnable)

    override val parent: ModeValueGroup<*>
        get() = modeValueGroup
}

internal class CrystalNormalRotationMode(
    modeValueGroup: ModeValueGroup<CrystalDestroyRotationMode>,
    owner: EventListener,
    private val priority: Priority,
) : CrystalDestroyRotationMode("Normal", modeValueGroup, owner) {

    private val rotationSettings = BlockPlacementRotationBridge.createSettings(this)
    private val rotations = tree(rotationSettings.valueGroup)
    private val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)

    override fun rotate(rotation: Rotation, isFinished: BooleanSupplier, onFinished: Runnable) {
        if (instant && isFinished.asBoolean) {
            onFinished.run()
            return
        }

        mc.execute {
            RotationManager.setRotationTarget(
                rotation,
                considerInventory = !ignoreOpenInventory,
                valueGroup = rotationSettings.targetFactory,
                provider = owner,
                priority = priority,
                whenReached = RestrictedSingleUseAction(canExecute = isFinished, action = {
                    BlockPlacementRotationBridge.schedule(owner, postMove, priority = true, task = onFinished)
                }),
            )
        }
    }
}

internal class CrystalNoRotationMode(
    modeValueGroup: ModeValueGroup<CrystalDestroyRotationMode>,
    owner: EventListener,
) : CrystalDestroyRotationMode("None", modeValueGroup, owner) {

    private val send by boolean("SendRotationPacket", false)

    override fun rotate(rotation: Rotation, isFinished: BooleanSupplier, onFinished: Runnable) {
        fun task() {
            if (send) {
                val fixedRotation = rotation.normalize()
                network.send(
                    ServerboundMovePlayerPacket.Rot(
                        fixedRotation.yaw,
                        fixedRotation.pitch,
                        player.onGround(),
                        player.horizontalCollision,
                    ),
                )
            }

            onFinished.run()
        }

        if (instant) {
            task()
            return
        }

        BlockPlacementRotationBridge.schedule(owner, postMove) { task() }
    }
}
