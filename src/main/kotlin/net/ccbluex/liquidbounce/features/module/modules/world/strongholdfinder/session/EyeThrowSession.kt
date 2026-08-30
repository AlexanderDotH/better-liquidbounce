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
package net.ccbluex.liquidbounce.features.module.modules.world.strongholdfinder.session

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap
import net.ccbluex.liquidbounce.utils.math.horizontalDistanceToSqr
import net.ccbluex.liquidbounce.utils.math.yaw
import net.ccbluex.liquidbounce.utils.world.stronghold.EyeMeasurement
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.projectile.EyeOfEnder
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

internal class EyeThrowSession {

    private val pendingThrows = ArrayDeque<PendingThrow>()
    private val trackedEyes = Int2ObjectLinkedOpenHashMap<TrackedEye>()

    fun recordThrow(
        throwPosition: Vec3,
        tick: Int,
        dimension: ResourceKey<Level>,
        maxSampleAgeTicks: Int,
    ) {
        trimPendingThrows(tick, maxSampleAgeTicks)
        pendingThrows.addLast(PendingThrow(throwPosition, tick, dimension))
    }

    fun trackSpawn(
        packet: ClientboundAddEntityPacket,
        dimension: ResourceKey<Level>,
        nowTick: Int,
        maxSampleAgeTicks: Int,
        maxEyeSpawnDistance: Float,
    ) {
        if (packet.type != EntityTypes.EYE_OF_ENDER) {
            return
        }

        trimPendingThrows(nowTick, maxSampleAgeTicks)
        val maxSpawnDistanceSqr = maxEyeSpawnDistance * maxEyeSpawnDistance
        val pending = pendingThrows
            .filter {
                it.dimension == dimension
                    && nowTick - it.tick in 0..maxSampleAgeTicks
                    && it.throwPosition.horizontalDistanceToSqr(packet.x, packet.z) <= maxSpawnDistanceSqr
            }
            .minWithOrNull(
                compareBy<PendingThrow> { it.throwPosition.horizontalDistanceToSqr(packet.x, packet.z) }
                    .thenComparingInt { nowTick - it.tick }
            ) ?: return

        pendingThrows.remove(pending)
        trackedEyes.put(packet.id, TrackedEye(packet.id, pending.throwPosition, nowTick))
    }

    fun captureMeasurements(
        world: ClientLevel,
        nowTick: Int,
        maxSampleAgeTicks: Int,
        sampleDelayTicks: Int,
        minEyeHorizontalSpeed: Float,
        onCaptured: (EyeMeasurement) -> Unit,
    ) {
        trimPendingThrows(nowTick, maxSampleAgeTicks)
        val trackedIterator = trackedEyes.int2ObjectEntrySet().iterator()
        while (trackedIterator.hasNext()) {
            val entry = trackedIterator.next()
            val trackedEye = entry.value

            if (nowTick - trackedEye.spawnTick < sampleDelayTicks) {
                continue
            }

            val eye = world.getEntity(entry.intKey) as? EyeOfEnder
            if (eye == null) {
                trackedIterator.remove()
                continue
            }

            if (eye.deltaMovement.horizontalDistance().toFloat() < minEyeHorizontalSpeed) {
                continue
            }

            val yaw = eye.position().subtract(trackedEye.throwPosition).yaw
            val measurement = EyeMeasurement(trackedEye.throwPosition, angleDeg = yaw, tick = nowTick)
            trackedIterator.remove()
            onCaptured(measurement)
        }
    }

    fun clear() {
        pendingThrows.clear()
        trackedEyes.clear()
    }

    private fun trimPendingThrows(nowTick: Int, maxSampleAgeTicks: Int) {
        while (pendingThrows.firstOrNull()?.let { nowTick - it.tick > maxSampleAgeTicks } == true) {
            pendingThrows.removeFirst()
        }
    }

    private data class PendingThrow(
        val throwPosition: Vec3,
        val tick: Int,
        val dimension: ResourceKey<Level>,
    )

    private data class TrackedEye(
        val entityId: Int,
        val throwPosition: Vec3,
        val spawnTick: Int,
    )
}
