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

package net.ccbluex.liquidbounce.features.module.modules.render.totemeffect.runtime

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.render.totemeffect.model.TotemPopSnapshot
import net.ccbluex.liquidbounce.render.WorldRenderEnvironment
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPush
import net.ccbluex.liquidbounce.utils.math.Easing

abstract class TotemEffectMode internal constructor(
    name: String,
    final override val parent: ModeValueGroup<*>,
    private val runtime: TotemEffectRuntime,
) : Mode(name) {

    val lifetime by int("lifetime", 20, 1..200).onChanged { runtime.entities.clear() }
    protected val fade by float("Fade", 0.7f, 0f..1f)
    protected val animCurve by easing("AnimCurve", Easing.EXPONENTIAL_OUT)
    protected val canBeCovered by boolean("CanBeCovered", false)

    protected abstract fun WorldRenderEnvironment
        .drawTotemEffect(progress: Float, age: Float, entity: TotemPopSnapshot, fade: Float)

    @Suppress("unused")
    private val renderHandler = handler<WorldRenderEvent> { event ->
        event.renderEnvironment {
            runtime.entities.forEach {
                val entity = it.value
                val age = lifetime - runtime.entities.timeToDie(it) + event.partialTicks

                val progress = animCurve
                    .transform(age / lifetime)
                    .coerceIn(0f, 1f)

                val fade = if (progress >= fade) (progress - fade) / (1f - fade).coerceAtLeast(0.001f) else 0f

                poseStack.withPush {
                    drawTotemEffect(progress, age, entity, fade)
                }
            }
        }
    }
}
