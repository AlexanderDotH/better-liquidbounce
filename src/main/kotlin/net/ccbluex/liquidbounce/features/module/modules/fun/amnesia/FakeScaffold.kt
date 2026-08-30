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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.playermodel.PlayerModelFakeScaffoldState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldStyle
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.ScaffoldYawMode
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.render.placement.PlacementRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity

object FakeScaffold : ToggleableValueGroup(
    null,
    "FakeScaffold",
    false,
    aliases = listOf("Fake Scaffold"),
) {

    internal val style by enumChoice("Style", ScaffoldStyle.NORMAL)
    internal val placeInterval by int("PlaceInterval", 4, 1..20, "ticks")
    internal val lifetime by int("Lifetime", 900, 100..3000, "ms")
    internal val maxBlocks by int("MaxBlocks", 12, 1..64)
    internal val renderBlocks by boolean("RenderBlocks", true)
    internal val spoofSwing by boolean("SpoofSwing", true)
    internal val spoofSneak by boolean("SpoofSneak", true)
    internal val spoofDownRotations by boolean("SpoofDownRotations", true)
    internal val pitch by float("Pitch", 78f, 45f..90f)
    internal val yawMode by enumChoice("YawMode", ScaffoldYawMode.MOVEMENT)

    private val renderer = tree(
        PlacementRenderer(
            "Render",
            true,
            this,
            keep = false,
            clump = true,
            defaultColor = Color4b(64, 180, 255, 80),
        )
    )
    private val renderedBlocks = LinkedHashSet<BlockPos>()

    @Suppress("unused")
    private val renderHandler = handler<GameRenderEvent>(priority = 2) {
        if (!running) {
            resetState()
            return@handler
        }

        val target = AmnesiaRuntimeBridge.findTarget() ?: run {
            resetState()
            return@handler
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val visualPos = AmnesiaRuntimeBridge.auxiliaryVisualPosition(target, partialTicks) ?: target.position()
        PlayerModelFakeScaffoldState.tick(
            target = target,
            partialTicks = partialTicks,
            visualPos = visualPos,
            style = style,
            placeInterval = placeInterval,
            lifetime = lifetime,
            maxBlocks = maxBlocks,
            spoofSwing = spoofSwing,
            spoofSneak = spoofSneak,
            spoofDownRotations = spoofDownRotations,
            pitch = pitch,
            yawMode = yawMode,
        )
        syncRenderer()
    }

    fun getTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!running) {
            return null
        }

        return PlayerModelFakeScaffoldState.getTransform(entity)
    }

    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!running) {
            return null
        }

        return PlayerModelFakeScaffoldState.getActionState(entity)
    }

    fun clearRenderState() {
        renderer.clearSilently()
        renderedBlocks.clear()
    }

    override fun onDisabled() {
        resetState()
    }

    private fun resetState() {
        PlayerModelFakeScaffoldState.reset()
        clearRenderState()
    }

    private fun syncRenderer() {
        val activeBlocks = if (renderBlocks) {
            PlayerModelFakeScaffoldState.activeBlockPositions()
        } else {
            emptySet()
        }

        val iterator = renderedBlocks.iterator()
        while (iterator.hasNext()) {
            val pos = iterator.next()
            if (pos !in activeBlocks) {
                renderer.removeBlock(pos)
                iterator.remove()
            }
        }

        for (pos in activeBlocks) {
            if (renderedBlocks.add(pos)) {
                renderer.addBlock(pos, update = false)
            }
        }

        renderer.updateAll()
    }
}
