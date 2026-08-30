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

import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.player.cheststealer.features.FeatureChestAura
import net.ccbluex.liquidbounce.features.module.modules.render.storageesp.StorageBoxMode
import net.ccbluex.liquidbounce.features.module.modules.render.storageesp.StorageCategoryResolver
import net.ccbluex.liquidbounce.features.module.modules.render.storageesp.StorageShapeCollector
import net.ccbluex.liquidbounce.features.module.modules.render.storageesp.StorageShaderMode
import net.ccbluex.liquidbounce.features.module.modules.render.storageesp.StorageTracerRenderer
import net.ccbluex.liquidbounce.features.module.modules.render.storageesp.TracedStorageEspCategory
import net.ccbluex.liquidbounce.render.engine.esp.EspChamsStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspChamsStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspFeatureRendererRegistry
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspOutlineStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspOutlineStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.StorageShaderEffect
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.utils.DistanceFadeUniformValueGroup
import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.utils.entity.cameraDistanceSq
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.awt.Color

/**
 * StorageESP module
 *
 * Allows you to see chests, dispensers, etc. through walls.
 */

object ModuleStorageESP : ClientModule("StorageESP", ModuleCategories.RENDER, aliases = listOf("ChestESP")) {

    internal val modes = choices("Mode", GlowMode, arrayOf(BoxMode, OutlineMode, GlowMode, ChamsMode))

    sealed class ChestType(
        name: String,
        defaultColor: Color4b,
    ) : ToggleableValueGroup(this, name, enabled = true), TracedStorageEspCategory {
        override val color by color("Color", defaultColor).onChanged {
            markDirtyForModes()
        }
        override val tracers by boolean("Tracers", false)

        override fun shouldRender(pos: BlockPos, ignoreDistance: Boolean): Boolean =
            this.running
                && pos !in FeatureChestAura.interactedBlocksSet
                && (ignoreDistance || pos.cameraDistanceSq() < distanceFade.farEnd.sq())

        fun shouldRender(pos: BlockPos): Boolean = shouldRender(pos, ignoreDistance = false)

        override fun shouldRender(entity: Entity, ignoreDistance: Boolean): Boolean =
            this.running
                && (ignoreDistance || entity.position().cameraDistanceSq() < distanceFade.farEnd.sq())

        fun shouldRender(entity: Entity): Boolean = shouldRender(entity, ignoreDistance = false)

        object Chest : ChestType("Chest", Color4b(0, 100, 255))
        object Barrel : ChestType("Barrel", Color4b(0xf6, 0x82, 0x1f))
        object EnderChest : ChestType("EnderChest", Color4b(Color.MAGENTA))
        object Furnace : ChestType("Furnace", Color4b(79, 79, 79))
        object BrewingStand : ChestType("BrewingStand", Color4b(139, 69, 19))
        object Dispenser : ChestType("Dispenser", Color4b(Color.LIGHT_GRAY))
        object Hopper : ChestType("Hopper", Color4b(Color.GRAY))
        object Minecart : ChestType("Minecart", Color4b(255, 85, 85))
        object ShulkerBox : ChestType("ShulkerBox", Color4b(Color(0x6e, 0x4d, 0x6e).brighter()))
        object Pot : ChestType("Pot", Color4b(209, 134, 0))
        object Bookshelf : ChestType("Bookshelf", Color4b(139, 90, 43))
        object Shelf : ChestType("Shelf", Color4b(160, 82, 45))
    }

    private val allTypes = arrayOf(
        ChestType.Chest,
        ChestType.Barrel,
        ChestType.EnderChest,
        ChestType.Furnace,
        ChestType.BrewingStand,
        ChestType.Dispenser,
        ChestType.Hopper,
        ChestType.Minecart,
        ChestType.ShulkerBox,
        ChestType.Pot,
        ChestType.Bookshelf,
        ChestType.Shelf,
    )

    init {
        allTypes.forEach { tree(it) }
        EspFeatureRendererRegistry.registerGlow(
            id = "module:storage_esp",
            source = EspGlowSource.STORAGE_ESP,
            style = { GlowMode.style.takeIf { GlowMode.running } },
            drawMask = GlowMode::drawMask,
        )
        EspFeatureRendererRegistry.registerOutline(
            id = "module:storage_esp",
            layer = EspMaskLayer.STORAGE_OUTLINE,
            style = { OutlineMode.style.takeIf { OutlineMode.running } },
            drawMask = OutlineMode::drawMask,
        )
        EspFeatureRendererRegistry.registerChams(
            id = "module:storage_esp",
            layer = EspMaskLayer.STORAGE_CHAMS,
            style = { ChamsMode.style.takeIf { ChamsMode.running } },
            drawMask = ChamsMode::drawMask,
        )
    }

    private val mergeAdjacent by boolean("MergeAdjacent", false).onChanged {
        markDirtyForModes()
    }

    private val distanceFade = tree(DistanceFadeUniformValueGroup())

    private val shapeCollector = StorageShapeCollector(
        entries = { StorageScanner.iterate() },
        mergeAdjacent = { mergeAdjacent },
    )

    private val tracerRenderer = StorageTracerRenderer(
        types = allTypes,
        blockPositions = StorageScanner::iterate,
        categorizeEntity = { it.categorize() },
    )

    override fun onEnabled() = ChunkScanner.subscribe(StorageScanner)

    override fun onDisabled() = ChunkScanner.unsubscribe(StorageScanner)

    private object BoxMode : StorageBoxMode(
        moduleName = ModuleStorageESP.name,
        parentProvider = { modes },
        distanceFadeProvider = { distanceFade },
        hasTrackedBlocks = { !StorageScanner.isEmpty() },
        collectTrackedShapes = { shapeCollector.collect() },
        categorizeEntity = { it.categorize() },
    )

    sealed class ShaderMode(
        name: String,
        effect: StorageShaderEffect,
    ) : StorageShaderMode(
        name = name,
        moduleName = ModuleStorageESP.name,
        effect = effect,
        parentProvider = { modes },
        distanceFadeProvider = { distanceFade },
        hasTrackedBlocks = { !StorageScanner.isEmpty() },
        collectTrackedShapes = shapeCollector::collect,
    )

    object GlowMode : ShaderMode("Glow", StorageShaderEffect.GLOW) {
        private val styleConfig = EspGlowStyleConfig(this)

        internal val style: EspGlowStyle
            get() = styleConfig.style
    }

    object OutlineMode : ShaderMode("Outline", StorageShaderEffect.OUTLINE) {
        private val styleConfig = EspOutlineStyleConfig(this)

        internal val style: EspOutlineStyle
            get() = styleConfig.style
    }

    object ChamsMode : ShaderMode("Chams", StorageShaderEffect.CHAMS) {
        private val styleConfig = EspChamsStyleConfig(this)

        internal val style: EspChamsStyle
            get() = styleConfig.style
    }

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        tracerRenderer.render(event)
    }

    @JvmStatic
    fun Entity?.categorize(): ChestType? = StorageCategoryResolver.kindOf(this)?.let { allTypes[it.ordinal] }

    @JvmStatic
    fun BlockEntity?.categorize(): ChestType? = StorageCategoryResolver.kindOf(this)?.let { allTypes[it.ordinal] }

    private fun markDirtyForModes() {
        GlowMode.markDirty()
        OutlineMode.markDirty()
        ChamsMode.markDirty()
        BoxMode.markDirty()
    }

    private object StorageScanner : AbstractBlockLocationTracker.State2BlockPos<ChestType>() {
        override fun getStateFor(pos: BlockPos, state: BlockState): ChestType? {
            if (!state.hasBlockEntity()) return null

            val chunk = mc.level?.getChunk(pos) ?: return null
            return chunk.getBlockEntity(pos)?.categorize()
        }

        override fun onUpdated() {
            markDirtyForModes()
        }
    }

    fun showTracers(): Boolean = running && allTypes.any { it.tracers }
}
