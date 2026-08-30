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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerNetworkMovementTickEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandCenter
import net.ccbluex.liquidbounce.features.command.commands.ingame.CommandCenter.CenterHandlerState
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.surround.config.SurroundDisableCondition
import net.ccbluex.liquidbounce.features.module.modules.world.surround.config.SurroundFeature
import net.ccbluex.liquidbounce.features.module.modules.world.surround.planner.SurroundGeometry
import net.ccbluex.liquidbounce.features.module.modules.world.surround.runtime.SurroundProtection
import net.ccbluex.liquidbounce.utils.block.DIRECTIONS_EXCLUDING_UP
import net.ccbluex.liquidbounce.utils.block.getBlockingEntities
import net.ccbluex.liquidbounce.features.block.placer.BlockPlacer
import net.ccbluex.liquidbounce.features.block.placer.placeInstantOnBlockUpdate
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.collection.getSlot
import net.ccbluex.liquidbounce.utils.entity.getFeetBlockPos
import net.ccbluex.liquidbounce.utils.entity.isInHole
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.center
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.level.block.Blocks
import org.joml.Vector2d
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Surround module
 *
 * Builds safe holes.
 *
 * @author ccetl
 */
object ModuleSurround : ClientModule("Surround", ModuleCategories.WORLD, disableOnQuit = true) {

    /**
     * The blocks the surround normal utilizes.
     */
    private val DEFAULT_BLOCKS = arrayOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.CRYING_OBSIDIAN)

    private val features by multiEnumChoice("Features",
        SurroundFeature.EXTEND,
        SurroundFeature.DOWN,
    )

    /**
     * Disables the module when the y-coordinate changes.
     * Or when the player has moved at least 0.5 blocks away from the original center.
     * Or when the player has a speed that is faster than or equal to 5 m/s.
     */
    private val disableOn by multiEnumChoice("DisableOn", SurroundDisableCondition.Y_CHANGE)

    /**
     * Replaces broken blocks instantly.
     *
     * Note: requires the rotation mode "None" in the block placer
     */
    private val instant by boolean("Instant", true)

    /**
     * Manually triggers the protection mechanism's extra layer.
     */
    private val addExtraLayer by bind("AddExtraLayer")
    private val protect = SurroundProtection(this, { placer }, { addExtraLayerBlocks })

    init {
        tree(protect)
    }

    private val filter by enumChoice("Filter", Filter.WHITELIST)
    private val blocks by blocks("Blocks", blockSortedSetOf(blocks = DEFAULT_BLOCKS))
    private val placer = tree(BlockPlacer(
        "Placing",
        this,
        Priority.IMPORTANT_FOR_PLAYER_LIFE,
        { filter.getSlot(blocks) }
    ))

    private var addExtraLayerBlocks = false
    private var startY = 0.0
    private val centerPos = Vector2d()

    init {
        // for this module, support should by default be able to use obsidian
        placer.support.blocks.addAll(DEFAULT_BLOCKS)
    }

    override fun onEnabled() {
        if (SurroundFeature.CENTER in features) {
            CommandCenter.state = CenterHandlerState.APPLY_ON_NEXT_EVENT
        }

        startY = player.position().y
        val centerBlockPos = player.blockPosition().center
        centerPos.set(centerBlockPos.x, centerBlockPos.z)
    }

    override fun onDisabled() {
        placer.disable()
        addExtraLayerBlocks = false
        centerPos.set(0.0)
    }

    @Suppress("unused")
    val keyHandler = handler<KeyboardKeyEvent> {
        addExtraLayerBlocks = addExtraLayer.getNewState(it, addExtraLayerBlocks)
    }

    @Suppress("unused", "MagicNumber")
    private val tickMoveHandler = handler<PlayerNetworkMovementTickEvent> {
        if (it.state == EventState.PRE) {
            return@handler
        }

        val yChange = SurroundDisableCondition.Y_CHANGE in disableOn && it.y != startY
        val dx = abs(player.x - centerPos.x)
        val dz = abs(player.z - centerPos.y)
        val xzChange = SurroundDisableCondition.XZ_MOVE in disableOn && (dx > 0.5 || dz > 0.5)
        val speed = player.position().subtract(player.xo, player.yo, player.zo).lengthSqr() * 20.0
        val highSpeed = SurroundDisableCondition.XZ_SPEED in disableOn && speed >= 5.0
        if (yChange || xzChange || highSpeed) {
            enabled = false
        }
    }

    @Suppress("unused")
    private val targetUpdater = handler<RotationUpdateEvent> {
        if (SurroundDisableCondition.Y_CHANGE in disableOn && player.position().y != startY) {
            enabled = false
            return@handler
        }

        val bb = player.boundingBox
        val y = ceil(bb.minY)

        val feetBlockPos = player.getFeetBlockPos()
        val hole = if (SurroundFeature.NO_WASTE in features && player.isInHole(feetBlockPos)) {
            listOf(feetBlockPos)
        } else {
            val maxX = SurroundGeometry.getMax(bb, Direction.Axis.X)
            val maxZ = SurroundGeometry.getMax(bb, Direction.Axis.Z)
            listOf(
                BlockPos.containing(bb.minX, y, bb.minZ),
                BlockPos.containing(bb.minX, y, maxZ),
                BlockPos.containing(maxX, y, bb.minZ),
                BlockPos.containing(maxX, y, maxZ),
            )
        }

        val holeBlocks = hashSetOf<BlockPos>()
        val blocked = hashSetOf<BlockPos>()
        blocked.addAll(hole)

        for (holePos in hole) {
            DIRECTIONS_EXCLUDING_UP.forEach { direction ->
                val pos = holePos.relative(direction)
                if (pos in hole || !holeBlocks.add(pos)) {
                    return@forEach
                }

                val isDown = direction == Direction.DOWN
                if (isDown && SurroundFeature.DOWN in features) {
                    holeBlocks.add(holePos.relative(direction, 2))
                }

                if (!isDown && (addExtraLayerBlocks || protect.broken.contains(pos.asLong()))) {
                    holeBlocks.add(pos.relative(direction))
                    holeBlocks.add(pos.above())
                    if (protect.extraLayer.corners) {
                        holeBlocks.add(pos.relative(direction.clockWise))
                    }
                }

                if (!isDown && SurroundFeature.EXTEND in features) {
                    pos.getBlockingEntities(except = player) { it !is EndCrystal }.forEach {
                        SurroundGeometry.addEntitySurround(it, holeBlocks, blocked, y)
                    }
                }
            }
        }

        placer.update(holeBlocks)
    }

    @Suppress("unused")
    private val blockUpdateHandler = handler<PacketEvent> {
        if (!instant) {
            return@handler
        }

        placer.placeInstantOnBlockUpdate(it)
    }

}
