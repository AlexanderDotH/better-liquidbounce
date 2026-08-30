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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.place

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.ModuleCrystalAura
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.SubmoduleIdPredict
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.SwitchMode
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.geometry.findClosestPointOnBlockInLineWithCrystal
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.geometry.predictedCrystalBox
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.features.rotation.NoRotationMode
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.data.RotationWithVector
import net.ccbluex.liquidbounce.utils.aiming.utils.raytraceUpperBlockSide
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.block.stateOrEmpty
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.network.clickBlockWithSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.ccbluex.liquidbounce.utils.raytracing.raytraceBlock
import net.ccbluex.liquidbounce.render.placement.PlacementRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.max

private data class CrystalPlacementRotation(
    val rotation: RotationWithVector,
    val side: Direction,
)

object SubmoduleCrystalPlacer : ToggleableValueGroup(ModuleCrystalAura, "Place", true) {

    private val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)
    private val switchMode by enumChoice("Switch", SwitchMode.SILENT)
    val oldVersion by boolean("1_12_2", false)
    private val delay by int("Delay", 0, 0..1000, "ms")
    val range by float("Range", 4.5f, 1f..6f).onChanged {
        CrystalAuraPlaceTargetFactory.updateSphere()
    }

    val wallsRange by float("WallsRange", 4.5f, 0f..6f).onChanged {
        CrystalAuraPlaceTargetFactory.updateSphere()
    }

    /**
     * Only place crystals above the block.
     * Outdated setting.
     * Using this is normally not recommended.
     */
    val onlyAbove by boolean("OnlyAbove", false)

    private val sequenced by boolean("Sequenced", false)

    // only applies without OnlyAbove
    private val notFacingAway by boolean("NotFacingAway", false)

    // only applies without OnlyAbove
    private val jitter by boolean("Jitter", false)

    val placementRenderer = tree(
        PlacementRenderer( // TODO slide
            "TargetRendering",
            true,
            ModuleCrystalAura,
            clump = false,
            defaultColor = Color4b.WHITE.with(a = 90)
        )
    )

    private val chronometer = Chronometer()
    private var blockHitResult: BlockHitResult? = null

    // this is shit, but I can't think of a better way right now.
    // the problem with only one rotation is
    // that when the ca switches between two players very fast and one place is invalid it would fail
    private var previousRotations = ArrayDeque<Pair<Rotation, Rotation>>(2)

    fun tick(excludeIds: IntArray? = null) {
        if (!canAttemptPlacement()) return
        getSlot() ?: return
        CrystalAuraPlaceTargetFactory.updateTarget(excludeIds)
        removeFromRenderer()
        val targetPos = CrystalAuraPlaceTargetFactory.placementTarget ?: return
        val (rotation, side) = resolvePlacementRotation(targetPos) ?: return
        if (!prepareUnrotatedHitResult(rotation, targetPos)) return
        addToRenderer()
        updatePrevious(rotation)
        queuePlacing(rotation, targetPos, side)
    }

    private fun canAttemptPlacement(): Boolean =
        enabled && chronometer.hasAtLeastElapsed(delay.toLong())

    private fun resolvePlacementRotation(targetPos: BlockPos): CrystalPlacementRotation? {
        val notSameRotation = RotationManager.serverRotation != previousRotations.lastOrNull()?.first
        val rotationsNotToMatch = if (notSameRotation && jitter) {
            previousRotations.map { it.second }
        } else {
            null
        }

        return if (onlyAbove) {
            val rotation = raytraceUpperBlockSide(
                player.eyePosition,
                range.toDouble(),
                wallsRange.toDouble(),
                targetPos,
                rotationsNotToMatch = rotationsNotToMatch,
            ) ?: return null
            CrystalPlacementRotation(rotation, Direction.UP)
        } else {
            val predictedCrystal = predictedCrystalBox(targetPos)
            mc.execute {
                ModuleDebug.debugGeometry(
                    ModuleCrystalAura,
                    "predictedCrystal",
                    ModuleDebug.DebuggedBox(predictedCrystal, Color4b.RED.fade(0.4f)),
                )
            }
            val data = findClosestPointOnBlockInLineWithCrystal(
                player.eyePosition,
                range.toDouble(),
                wallsRange.toDouble(),
                targetPos,
                notFacingAway,
                rotationsNotToMatch,
            ) ?: return null
            CrystalPlacementRotation(data.first, data.second)
        }
    }

    private fun prepareUnrotatedHitResult(rotation: RotationWithVector, targetPos: BlockPos): Boolean {
        if (ModuleCrystalAura.rotationMode.activeMode !is NoRotationMode) return true

        blockHitResult = raytraceBlock(
            getMaxRange().toDouble(),
            rotation.rotation,
            targetPos,
            targetPos.stateOrEmpty,
        ) ?: return false
        return true
    }

    private fun queuePlacing(rotation: RotationWithVector, targetPos: BlockPos, side: Direction) {
        ModuleCrystalAura.rotationMode.activeMode.rotate(rotation.rotation, isFinished = {
            blockHitResult = raytraceBlock(
                getMaxRange().toDouble(),
                RotationManager.serverRotation,
                targetPos,
                targetPos.stateOrEmpty
            ) ?: return@rotate false

            return@rotate blockHitResult!!.type == HitResult.Type.BLOCK && blockHitResult!!.blockPos == targetPos
        }, onFinished = {
            if (!chronometer.hasAtLeastElapsed(delay.toLong())) {
                return@rotate
            }

            player.clickBlockWithSlot(
                blockHitResult?.withDirection(side) ?: return@rotate,
                getSlot() ?: return@rotate,
                swingMode,
                switchMode.slotSwitchPolicy,
                sequenced
            )

            SubmoduleIdPredict.run(targetPos)

            chronometer.reset()
        })
    }

    private fun updatePrevious(rotation: RotationWithVector) {
        if (previousRotations.size == 2) {
            previousRotations.removeFirst()
        }

        // stores the mutable rotation and a copy to compare with the produced rotations
        previousRotations.addLast(rotation.rotation to rotation.rotation.copy())
    }

    private fun addToRenderer() = with(CrystalAuraPlaceTargetFactory) {
        if (placementTarget == previousTarget) {
            return@with
        }

        placementTarget?.let {
            mc.execute { placementRenderer.addBlock(it) }
        }
    }

    private fun removeFromRenderer() = with(CrystalAuraPlaceTargetFactory) {
        if (placementTarget == previousTarget) {
            return@with
        }

        previousTarget?.let {
            mc.execute { placementRenderer.removeBlock(it) }
        }
    }

    private fun getSlot(): Int? {
        return Slots.OffhandWithHotbar.findClosestSlot(Items.END_CRYSTAL)?.inventorySlot
    }

    fun getMaxRange() = max(range, wallsRange)

}
