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
package net.ccbluex.liquidbounce.features.module.modules.world.nuker

import net.ccbluex.liquidbounce.event.events.BlockAttackEvent
import net.ccbluex.liquidbounce.event.events.BlockBreakingProgressEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.MouseRotationEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.area.FloorNukerArea
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.area.SphereNukerArea
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.mode.InstantNukerMode
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.mode.LegitNukerMode
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.render.BreakingProgress
import net.ccbluex.liquidbounce.utils.render.BreakingProgressRenderer
import net.ccbluex.liquidbounce.utils.render.placement.PlacementRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.lwjgl.glfw.GLFW

/**
 * Nuker module
 *
 * Destroys blocks around you.
 */
object ModuleNuker : ClientModule("Nuker", ModuleCategories.WORLD, disableOnQuit = true), BreakingProgress.Provider {

    val mode =
        choices("Mode", LegitNukerMode, arrayOf(LegitNukerMode, InstantNukerMode))
    val areaMode = choices(
        "AreaMode",
        SphereNukerArea,
        arrayOf(SphereNukerArea, FloorNukerArea)
    )
    private val blockRule by enumChoice("BlockRule", NukerBlockRule.SAME_BLOCK)
    private var selectedBlock: Block? = null
    private val playerInputOverride = NukerPlayerInputOverride()

    internal val playerInputOverridesRotation: Boolean
        get() = playerInputOverride.active

    val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)
    val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)

    private val targetRenderer = tree(
        PlacementRenderer("TargetRendering", true, this, keep = false)
    )
    private val progressRenderer = targetRenderer.tree(
        BreakingProgressRenderer(targetRenderer, this)
    )

    override fun breakingProgress(): BreakingProgress? = when (mode.activeMode) {
        LegitNukerMode -> LegitNukerMode.breakingProgress()
        InstantNukerMode -> wasTarget?.let { BreakingProgress(it, 1f) }
        else -> null
    }

    /**
     * The last target block that was hit. Does not mean it was destroyed.
     */
    var wasTarget: BlockPos? = null
        set(value) {
            field = value
            targetRenderer.addBlock(value ?: return)
        }

    fun isValid(blockState: BlockState) = blockRule.accepts(blockState.block, selectedBlock)

    @Suppress("unused")
    private val blockAttackHandler = handler<BlockAttackEvent> { event ->
        rememberManuallyMinedBlock(event.pos)
    }

    @Suppress("unused")
    private val blockBreakingProgressHandler = handler<BlockBreakingProgressEvent> { event ->
        rememberManuallyMinedBlock(event.pos)
    }

    @Suppress("unused")
    private val mouseRotationHandler = handler<MouseRotationEvent> { event ->
        if (mc.gui.screen() != null || event.cursorDeltaX == 0.0 && event.cursorDeltaY == 0.0) {
            return@handler
        }

        yieldToPlayerInput()
    }

    @Suppress("unused")
    private val mouseButtonHandler = handler<MouseButtonEvent> { event ->
        if (blockRule != NukerBlockRule.SAME_BLOCK ||
            event.screen != null ||
            event.action != GLFW.GLFW_PRESS ||
            event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT
        ) {
            return@handler
        }

        val hitResult = mc.hitResult as? BlockHitResult ?: return@handler
        if (hitResult.type != HitResult.Type.BLOCK) {
            return@handler
        }

        val block = selectBlock(hitResult.blockPos) ?: return@handler
        notification("Nuker", message("blockSelected", block.name.string), NotificationEvent.Severity.INFO)
    }

    @Suppress("unused")
    private val gameTickHandler = handler<GameTickEvent> {
        playerInputOverride.tick()
    }

    private fun rememberManuallyMinedBlock(pos: BlockPos) {
        val crosshairPos = (mc.hitResult as? BlockHitResult)?.blockPos
        if (!isManualNukerSelection(mc.options.keyAttack.isDown, crosshairPos, pos)) {
            return
        }

        selectBlock(pos)
    }

    /**
     * Selects a block type for SameBlock. Kept internal so explicit input actions can share one path.
     */
    internal fun selectBlock(pos: BlockPos): Block? {
        val state = world.getBlockState(pos)
        if (state.isAir) {
            return null
        }

        selectedBlock = state.block
        if (running) {
            yieldToPlayerInput()
        }

        return state.block
    }

    private fun yieldToPlayerInput() {
        playerInputOverride.activate()
        LegitNukerMode.releaseTargetForPlayerInput()
    }

    override fun onDisabled() {
        playerInputOverride.reset()
        LegitNukerMode.releaseTargetForPlayerInput()
        wasTarget = null
        targetRenderer.clearSilently()
    }

}
