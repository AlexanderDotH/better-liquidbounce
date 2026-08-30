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
package net.ccbluex.liquidbounce.event

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import org.joml.Matrix4fc

/**
 * Marks an event class that may be registered when an event API is used before client composition runs.
 */
interface RuntimeRegisteredEvent

/**
 * Exposes an event-owned environment without making its consumer depend on the concrete event class.
 */
interface EnvironmentEvent<E> {
    val environment: E
}

/**
 * Read-only render-frame state needed by consumers that render into a derived target.
 */
interface WorldRenderContext {
    val poseStack: PoseStack
    val modelViewMatrix: Matrix4fc
    val camera: Camera
    val renderTarget: RenderTarget
}
