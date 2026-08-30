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
package net.ccbluex.liquidbounce.features.block.placer

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BlockPlacerSequenceTest {

    @Test
    fun `support selection keeps the shortest path and fails closed without one`() {
        val long = linkedSetOf(BlockPos.ZERO, BlockPos(1, 0, 0), BlockPos(2, 0, 0))
        val short = linkedSetOf(BlockPos.ZERO)

        assertEquals(short, shortestSupportPath(listOf(null, long, short)))
        assertNull(shortestSupportPath(listOf(null, null)))
    }

    @Test
    fun `instant placement sends optional rotation before placement`() {
        val events = mutableListOf<String>()

        runInstantPlacement(sendRotation = true, send = { events += "rotation" }, place = { events += "place" })
        assertEquals(listOf("rotation", "place"), events)

        events.clear()
        runInstantPlacement(sendRotation = false, send = { events += "rotation" }, place = { events += "place" })
        assertEquals(listOf("place"), events)
    }

    @Test
    fun `crystal cleanup invokes target clearing exactly once`() {
        var clears = 0

        clearCrystalTarget { clears++ }

        assertEquals(1, clears)
    }

    @Test
    fun `crystal attack keeps attack reset and target cleanup order`() {
        val events = mutableListOf<String>()

        runCrystalAttack(
            attack = { events += "attack" },
            resetDelay = { events += "reset" },
            clearTarget = { events += "clear" },
        )

        assertEquals(listOf("attack", "reset", "clear"), events)
    }

    @Test
    fun `crystal destroyer depends only on its installed attack sink`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/block/placer/CrystalDestroyFeature.kt"
        ))
        val attack = source.indexOf("CrystalAttackSink.attack(target, swingMode)")
        val reset = source.indexOf("resetDelay = chronometer::reset", attack)
        val clear = source.indexOf("currentTarget = null", reset)

        assertTrue(attack >= 0 && attack < reset && reset < clear)
        assertFalse(source.contains("features.combat.runtime"))
        assertFalse(source.contains("attackEntity"))
    }

    @Test
    fun `extracted crystal rotation strategies preserve instant and queued action semantics`() {
        val source = Files.readString(Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/block/placer/CrystalDestroyRotationModes.kt"
        ))

        val normalInstant = source.indexOf("if (instant && isFinished.asBoolean)")
        val normalFinished = source.indexOf("onFinished.run()", normalInstant)
        val normalReturn = source.indexOf("return", normalFinished)
        val normalManagedRotation = source.indexOf("RotationManager.setRotationTarget", normalReturn)
        val prioritySchedule = source.indexOf(
            "BlockPlacementRotationBridge.schedule(owner, postMove, priority = true, task = onFinished)",
            normalManagedRotation,
        )
        val packetSend = source.indexOf("network.send(", prioritySchedule)
        val noRotationFinished = source.indexOf("onFinished.run()", packetSend)
        val noRotationInstant = source.indexOf("if (instant)", noRotationFinished)
        val queuedSchedule = source.indexOf("BlockPlacementRotationBridge.schedule(owner, postMove)", noRotationInstant)

        assertTrue(
            listOf(
                normalInstant,
                normalFinished,
                normalReturn,
                normalManagedRotation,
                prioritySchedule,
                packetSend,
                noRotationFinished,
                noRotationInstant,
                queuedSchedule,
            ).all { it >= 0 },
        )
        assertTrue(normalInstant < normalFinished && normalFinished < normalReturn)
        assertTrue(normalReturn < normalManagedRotation && normalManagedRotation < prioritySchedule)
        assertTrue(prioritySchedule < packetSend && packetSend < noRotationFinished)
        assertTrue(noRotationFinished < noRotationInstant && noRotationInstant < queuedSchedule)
    }
}
