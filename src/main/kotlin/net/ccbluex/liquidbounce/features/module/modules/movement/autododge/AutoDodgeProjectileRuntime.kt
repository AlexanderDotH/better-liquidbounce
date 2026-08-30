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

package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.entity.CachedPlayerSimulation
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulation
import net.ccbluex.liquidbounce.features.simulation.PlayerSimulationCache
import net.ccbluex.liquidbounce.utils.entity.SimulatedArrow
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.arrow.SpectralArrow
import net.minecraft.world.entity.projectile.arrow.ThrownTrident
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal class AutoDodgeProjectileRuntime : MinecraftShortcuts {
    fun findImmediateThreat(): ModuleAutoDodge.HitInfo? = getInflictedHits(
        simulatedPlayer = CachedPlayerSimulation(PlayerSimulationCache.getSimulationForLocalPlayer()),
        arrows = world.findFlyingArrows(),
        hitboxExpansion = DodgePlanner.SAFE_DISTANCE_WITH_PADDING,
    )

    fun findAvoidingArrowPosition(): ModuleAutoDodge.EvadingPacket? {
        var packetIndex = 0
        var lastPosition: Vec3? = null
        var bestPacketPosition: Vec3? = null
        var bestPacketIdx: Int? = null
        var bestTimeToImpact = 0
        for (position in BlinkManager.positions) {
            packetIndex++
            if (lastPosition != null && lastPosition.distanceToSqr(position) < MIN_PACKET_DISTANCE_SQ) continue
            lastPosition = position
            val inflictedHit = getInflictedHit(position)
            if (inflictedHit == null) return ModuleAutoDodge.EvadingPacket(packetIndex - 1, null)
            if (inflictedHit.tickDelta > bestTimeToImpact) {
                bestTimeToImpact = inflictedHit.tickDelta
                bestPacketIdx = packetIndex - 1
                bestPacketPosition = position
            }
        }
        val packetPosition = bestPacketPosition
        if (bestPacketIdx != null && packetPosition != null &&
            packetPosition.distanceToSqr(player.position()) > MIN_PACKET_DISTANCE_SQ
        ) {
            return ModuleAutoDodge.EvadingPacket(bestPacketIdx, bestTimeToImpact)
        }
        return null
    }

    fun getInflictedHit(pos: Vec3): ModuleAutoDodge.HitInfo? = getInflictedHits(
        simulatedPlayer = PlayerSimulation.Rigid(pos),
        arrows = world.findFlyingArrows(),
        maxTicks = 40,
    )

    private fun ClientLevel.findFlyingArrows() = entitiesForRendering().filter { entity ->
        (entity is Arrow || entity is SpectralArrow ||
            entity is ThrownTrident && entity.clientSideReturnTridentTickCount == 0) && !entity.isInGround
    }

    private fun <T : PlayerSimulation> getInflictedHits(
        simulatedPlayer: T,
        arrows: List<Entity>,
        maxTicks: Int = 80,
        hitboxExpansion: Double = 0.7,
    ): ModuleAutoDodge.HitInfo? {
        val simulatedArrows = arrows.mapToArray {
            SimulatedArrow(it.level(), it.position(), it.deltaMovement, false)
        }
        repeat(maxTicks) { tick ->
            simulatedPlayer.tick()
            simulatedArrows.forEachIndexed { arrowIndex, arrow ->
                if (arrow.inGround) return@forEachIndexed
                val lastPos = arrow.pos
                arrow.tick()
                val hitBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
                    .inflate(hitboxExpansion)
                    .move(simulatedPlayer.pos)
                hitBox.clip(lastPos, arrow.pos).orElse(null)?.let { hitPos ->
                    return ModuleAutoDodge.HitInfo(
                        tick,
                        arrows[arrowIndex],
                        hitPos,
                        lastPos,
                        arrow.velocity,
                    )
                }
            }
        }
        return null
    }

    private companion object {
        const val MIN_PACKET_DISTANCE = 0.9
        const val MIN_PACKET_DISTANCE_SQ = MIN_PACKET_DISTANCE * MIN_PACKET_DISTANCE
    }
}
