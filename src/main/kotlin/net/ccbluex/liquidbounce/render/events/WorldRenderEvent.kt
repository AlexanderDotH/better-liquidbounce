/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.render.events

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
import net.ccbluex.liquidbounce.annotations.Tag
import net.ccbluex.liquidbounce.event.EnvironmentEvent
import net.ccbluex.liquidbounce.event.Event
import net.ccbluex.liquidbounce.event.RuntimeRegisteredEvent
import net.ccbluex.liquidbounce.event.WorldRenderContext
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.minecraft.client.Camera
import org.joml.Matrix4fc

@Tag("worldRender")
class WorldRenderEvent(
    override val poseStack: PoseStack,
    override val modelViewMatrix: Matrix4fc,
    override val camera: Camera,
    val partialTicks: Float,
    override val renderTarget: RenderTarget,
) : Event(),
    RuntimeRegisteredEvent,
    EnvironmentEvent<WorldRenderEnvironment>,
    WorldRenderContext,
    AutoCloseable {
    @Deprecated("For scripts only", ReplaceWith("poseStack"))
    val matrixStack get() = poseStack

    override val environment = WorldRenderEnvironment.create(renderTarget, poseStack, camera)

    override fun close() {
        environment.flush(modelViewMatrix)
    }
}
