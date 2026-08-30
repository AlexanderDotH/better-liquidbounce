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
package net.ccbluex.liquidbounce.deeplearn.command

import net.ccbluex.liquidbounce.deeplearn.data.TrainingSample
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CommandModelsContractTest {
    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `models retains subcommand and parameter order`() {
        val command = CommandModels.createCommand()

        assertEquals(listOf("create", "improve", "delete", "reload", "browse"), command.subcommands.map { it.name })
        assertEquals(listOf("name"), command.subcommands[0].parameters.map { it.name })
        assertEquals(listOf("name"), command.subcommands[1].parameters.map { it.name })
        assertEquals(listOf("name"), command.subcommands[2].parameters.map { it.name })
    }

    @Test
    fun `dataset preparation preserves sample ordering`() {
        val dataset = prepareTrainingDataset(
            listOf(
                Sample(floatArrayOf(1f, 2f), floatArrayOf(3f)),
                Sample(floatArrayOf(4f, 5f), floatArrayOf(6f)),
            ),
        )

        assertArrayEquals(floatArrayOf(1f, 2f, 4f, 5f), dataset.features)
        assertArrayEquals(floatArrayOf(3f, 6f), dataset.labels)
    }

    @Test
    fun `dataset preparation rejects inconsistent sample sizes`() {
        assertThrows(IllegalArgumentException::class.java) {
            prepareTrainingDataset(
                listOf(
                    Sample(floatArrayOf(1f), floatArrayOf(2f)),
                    Sample(floatArrayOf(3f, 4f), floatArrayOf(5f)),
                ),
            )
        }
    }

    private class Sample(
        private val inputs: FloatArray,
        private val outputs: FloatArray,
    ) : TrainingSample {
        override val inputSize = inputs.size
        override val outputSize = outputs.size

        override fun fillAsInput(dest: FloatArray, fromIndex: Int): Int {
            inputs.copyInto(dest, fromIndex)
            return fromIndex + inputs.size
        }

        override fun fillAsOutput(dest: FloatArray, fromIndex: Int): Int {
            outputs.copyInto(dest, fromIndex)
            return fromIndex + outputs.size
        }
    }
}
