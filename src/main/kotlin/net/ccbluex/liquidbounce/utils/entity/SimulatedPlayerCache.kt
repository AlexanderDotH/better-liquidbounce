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
 */

package net.ccbluex.liquidbounce.utils.entity

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectImmutableList
import net.minecraft.world.phys.Vec3
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class SimulatedPlayerCache(internal val simulatedPlayer: SimulatedPlayer) {
    private var currentSimulationStep = 0
    private val simulationSteps = ObjectArrayList<SimulatedPlayerSnapshot>().apply {
        add(SimulatedPlayerSnapshot(simulatedPlayer))
    }
    private val lock = ReentrantReadWriteLock()

    fun simulateUntil(ticks: Int) {
        check(ticks >= 0) { "ticks may not be negative" }
        if (currentSimulationStep >= ticks) return

        lock.write {
            while (currentSimulationStep < ticks) {
                simulatedPlayer.tick()
                simulationSteps.add(SimulatedPlayerSnapshot(simulatedPlayer))
                currentSimulationStep++
            }
        }
    }

    fun getSnapshotAt(ticks: Int): SimulatedPlayerSnapshot {
        simulateUntil(ticks)
        return lock.read { simulationSteps[ticks] }
    }

    fun simulate(): Sequence<SimulatedPlayerSnapshot> = generateSequence(0) { it + 1 }.map(::getSnapshotAt)

    fun getSnapshotsBetween(tickRange: IntRange): List<SimulatedPlayerSnapshot> {
        validateRange(tickRange)
        simulateUntil(tickRange.last + 1)
        return lock.read { ObjectImmutableList(simulationSteps.subList(tickRange.first, tickRange.last + 1)) }
    }

    fun simulateBetween(tickRange: IntRange): Sequence<SimulatedPlayerSnapshot> {
        validateRange(tickRange)
        simulateUntil(tickRange.last + 1)
        return tickRange.asSequence().map(::getSnapshotAt)
    }

    private fun validateRange(tickRange: IntRange) {
        check(tickRange.last < 60 * 20) { "tried to simulate a player for more than a minute!" }
    }
}

data class SimulatedPlayerSnapshot(
    val pos: Vec3,
    val fallDistance: Double,
    val velocity: Vec3,
    val onGround: Boolean,
    val clipLedged: Boolean,
) {
    constructor(simulatedPlayer: SimulatedPlayer) : this(
        simulatedPlayer.pos,
        simulatedPlayer.fallDistance,
        simulatedPlayer.deltaMovement,
        simulatedPlayer.onGround,
        simulatedPlayer.clipLedged,
    )
}

class CachedPlayerSimulation(val simulatedPlayer: SimulatedPlayerCache) : PlayerSimulation {
    override val pos: Vec3
        get() = simulatedPlayer.getSnapshotAt(ticks).pos

    private var ticks = 0

    override fun tick() {
        ticks++
    }
}
