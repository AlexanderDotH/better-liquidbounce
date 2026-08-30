/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.render.target

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.utils.entity.box
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.math.sin

internal sealed class TargetHeightMode(name: String) : Mode(name) {
    abstract fun getHeight(entity: Entity, partialTicks: Float): Double

    interface WithGlow {
        fun getGlowHeight(entity: Entity, partialTicks: Float): Double
    }

    class Feet(override val parent: ModeValueGroup<*>) : TargetHeightMode("Feet") {
        private val offset by float("Offset", 0f, -1f..1f)
        override fun getHeight(entity: Entity, partialTicks: Float): Double = offset.toDouble()
    }

    class Top(override val parent: ModeValueGroup<*>) : TargetHeightMode("Top") {
        private val offset by float("Offset", 0f, -1f..1f)
        override fun getHeight(entity: Entity, partialTicks: Float) = entity.box.maxY - entity.box.minY + offset
    }

    class Relative(override val parent: ModeValueGroup<*>) : TargetHeightMode("Relative") {
        private val height by float("Height", 0.5f, -0.5f..1.5f)
        override fun getHeight(entity: Entity, partialTicks: Float): Double {
            val box = entity.box
            return height * (box.maxY - box.minY)
        }
    }

    class Health(override val parent: ModeValueGroup<*>) : TargetHeightMode("Health") {
        override fun getHeight(entity: Entity, partialTicks: Float): Double {
            if (entity !is LivingEntity) return 0.0
            val box = entity.box
            return entity.health / entity.maxHealth * (box.maxY - box.minY)
        }
    }

    class Animated(override val parent: ModeValueGroup<*>) : TargetHeightMode("Animated"), WithGlow {
        private val speed by float("Speed", 0.18f, 0.01f..1f)
        private val heightMultiplier by float("HeightMultiplier", 0.4f, 0.1f..1f)
        private val heightOffset by float("HeightOffset", 1.3f, 0f..2f)
        private val glowOffset by float("GlowOffset", -1f, -3.1f..3.1f)

        override fun getHeight(entity: Entity, partialTicks: Float) =
            calculateHeight((entity.tickCount + partialTicks) * speed)

        override fun getGlowHeight(entity: Entity, partialTicks: Float) =
            calculateHeight((entity.tickCount + partialTicks) * speed + glowOffset)

        private fun calculateHeight(time: Float) = sin(time.toDouble()) * heightMultiplier + heightOffset
    }
}
