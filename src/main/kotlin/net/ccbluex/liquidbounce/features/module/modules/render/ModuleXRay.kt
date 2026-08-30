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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.fastutil.Pool.Companion.use
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.render.xray.contract.XRayCommandActions
import net.ccbluex.liquidbounce.features.module.modules.render.xray.contract.XRayCommandBridge
import net.ccbluex.liquidbounce.features.module.modules.render.xray.defaultXRayBlocks
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.collection.Pools
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes

/**
 * XRay module
 *
 * Allows you to see ores through walls.
 */
object ModuleXRay : ClientModule("XRay", ModuleCategories.RENDER) {

    // Lighting of blocks through walls
    val fullBright by boolean("FullBright", true)
        .onChanged(::valueChangedReload)

    // Only render blocks with non-solid blocks around
    private val exposedOnly by boolean("ExposedOnly", false)
        .onChanged(::valueChangedReload)

    val backgroundOpacity by int("BackgroundOpacity", 0, 0..255)
        .onChanged(::valueChangedReload)

    private val defaultBlocks = defaultXRayBlocks()

    // Set of blocks that will not be excluded
    val blocks: MutableSet<Block> by blocks(
        "Blocks",
        defaultXRayBlocks()
    ).onChanged(::valueChangedReload)

    init {
        XRayCommandBridge.install(
            XRayCommandActions(
                blocks = { blocks },
                add = blocks::add,
                remove = blocks::remove,
                clear = blocks::clear,
                reset = ::applyDefaults,
            )
        )
    }

    /**
     * Checks if the block should be rendered or not.
     * This can be used to exclude blocks that should not be rendered.
     * Also features an option to only render blocks that are exposed to air.
     */
    fun shouldRender(blockState: BlockState, blockPos: BlockPos) = when {
        blockState.block !in blocks -> false

        exposedOnly -> Pools.MutableBlockPos.use { pos ->
            Direction.entries.any {
                pos.set(blockPos).move(it.unitVec3i).state?.isRedstoneConductor(world, pos) == false
            }
        }

        else -> true
    }

    fun shouldRenderTransparentBackground(blockState: BlockState) =
        backgroundOpacity > 0 && blockState.block !in blocks && !blockState.isAir

    fun shouldSkipRender(blockState: BlockState, blockPos: BlockPos) =
        !shouldRender(blockState, blockPos) && !shouldRenderTransparentBackground(blockState)

    fun transparentBackgroundAlpha(blockState: BlockState) =
        if (shouldRenderTransparentBackground(blockState)) backgroundOpacity else 255

    /**
     * Keeps vanilla/Sodium face culling unless this is a whitelisted XRay block hidden behind another block.
     *
     * @see net.minecraft.client.renderer.block.ModelBlockRenderer.shouldRenderFace
     * @see net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext.shouldDrawSide
     */
    fun modifyDrawSide(
        blockState: BlockState,
        level: BlockGetter,
        blockPos: BlockPos,
        side: Direction,
        original: Boolean
    ): Boolean {
        if (original || !shouldRender(blockState, blockPos)) {
            return original
        }

        val adjacentPos = blockPos.relative(side)
        val adjacentState = level.getBlockState(adjacentPos)

        return adjacentState.getFaceOcclusionShape(side.opposite) != Shapes.block()
            || adjacentState.block != blockState.block
            || !adjacentState.isSolidRender
            || !shouldRender(adjacentState, adjacentPos)
    }

    fun shouldRender(state: BlockState, otherState: BlockState, side: Direction) = when {
        state.block !in blocks -> false

        exposedOnly -> !state.skipRendering(otherState, side)

        else -> true
    }

    fun modifyShouldRenderFace(original: Boolean, state: BlockState, otherState: BlockState, side: Direction) =
        if (shouldRenderTransparentBackground(state)) {
            original
        } else {
            shouldRender(state, otherState, side)
        }

    /**
     * Resets the block list to the default values
     */
    fun applyDefaults() {
        blocks.clear()
        blocks.addAll(defaultBlocks)
    }

    override fun onEnabled() {
        mc.levelExtractor.allChanged()
    }

    override fun onDisabled() {
        mc.levelExtractor.allChanged()
    }

    @Suppress("UNUSED_PARAMETER")
    fun valueChangedReload(it: Any) {
        if (!running) return

        mc.execute {
            // Reload world renderer on block list change
            mc.levelExtractor.allChanged()
        }
    }

}
