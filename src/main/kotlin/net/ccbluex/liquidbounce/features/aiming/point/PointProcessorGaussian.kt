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

package net.ccbluex.liquidbounce.features.aiming.point

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.utils.percentageChance
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.aiming.point.PointInsideBox
import net.ccbluex.liquidbounce.utils.entity.horizontalSpeed
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.equals
import net.minecraft.util.Mth.lerp
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.security.SecureRandom

internal class PointProcessorGaussian(parent: EventListener) : PointProcessor(parent, "Gaussian", false) {

    companion object {

        /**
         * The gaussian distribution values for the offset.
         */
        private const val STDDEV_Z = 0.24453708645460387
        private const val MEAN_X = 0.00942273861037109
        private const val STDDEV_X = 0.23319837528201348
        private const val MEAN_Y = -0.30075078007595923
        private const val STDDEV_Y = 0.3492437109081718
        private const val MEAN_Z = 0.013282929419023442

        private val random = SecureRandom()

    }

    private var currentOffset: Vec3 = Vec3.ZERO
    private var targetOffset: Vec3 = Vec3.ZERO

    private val yawFactor by floatRange("YawOffset", 0f..0f, 0.0f..1.0f)
    private val pitchFactor by floatRange("PitchOffset", 0f..0f, 0.0f..1.0f)
    private val chance = percentageChance("Chance", 100f) { random }
    private val speed by floatRange("Speed", 0.1f..0.2f, 0.01f..1f)
    private val tolerance by float("Tolerance", 0.05f, 0.01f..0.1f)

    private inner class Dynamic : ToggleableValueGroup(this, "Dynamic", false) {
        val hurtTime by int("HurtTime", 10, 0..10)
        val yawFactor by float("YawFactor", 0f, 0f..10f, "x")
        val pitchFactor by float("PitchFactor", 0f, 0f..10f, "x")
        val speed by floatRange("Speed", 0.5f..0.75f, 0.01f..1f)
        val tolerance by float("Tolerance", 0.1f, 0.01f..0.1f)
    }

    private val dynamic = tree(Dynamic())

    fun updateGaussianOffset(entity: Any?) {
        val useDynamic = dynamic.enabled && entity is LivingEntity && entity.hurtTime >= dynamic.hurtTime
        advanceOffset(useDynamic, sampleYawFactor(useDynamic), samplePitchFactor(useDynamic))
    }

    private fun sampleYawFactor(useDynamic: Boolean): Double {
        val sampledFactor = yawFactor.random()
        return if (useDynamic && dynamic.yawFactor > 0f) {
            (sampledFactor + player.horizontalSpeed * dynamic.yawFactor).toDouble()
        } else {
            sampledFactor.toDouble()
        }
    }

    private fun samplePitchFactor(useDynamic: Boolean): Double {
        val sampledFactor = pitchFactor.random()
        return if (useDynamic && dynamic.pitchFactor > 0f) {
            (sampledFactor + player.horizontalSpeed * dynamic.pitchFactor).toDouble()
        } else {
            sampledFactor.toDouble()
        }
    }

    private fun advanceOffset(useDynamic: Boolean, sampledYawFactor: Double, sampledPitchFactor: Double) {
        if (currentOffset.equals(targetOffset, tolerance(useDynamic))) {
            if (chance.asBoolean) {
                targetOffset = Vec3(
                    random.nextGaussian(MEAN_X, STDDEV_X) * sampledYawFactor,
                    random.nextGaussian(MEAN_Y, STDDEV_Y) * sampledPitchFactor,
                    random.nextGaussian(MEAN_Z, STDDEV_Z) * sampledYawFactor
                )
            }
            return
        }

        currentOffset = Vec3(
            lerp(speed(useDynamic), currentOffset.x, targetOffset.x),
            lerp(speed(useDynamic), currentOffset.y, targetOffset.y),
            lerp(speed(useDynamic), currentOffset.z, targetOffset.z),
        )
    }

    private fun tolerance(useDynamic: Boolean): Double =
        (if (useDynamic) dynamic.tolerance else tolerance).toDouble()

    private fun speed(useDynamic: Boolean): Double =
        (if (useDynamic) dynamic.speed.random() else speed.random()).toDouble()

    override fun process(point: PointInsideBox): PointInsideBox {
        if (yawFactor.random() > 0.0f && pitchFactor.random() > 0.0f && chance.isEnabled) {
            updateGaussianOffset(point)
        }

        return point + currentOffset
    }

}
