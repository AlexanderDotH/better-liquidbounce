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
package net.ccbluex.liquidbounce.features.rotation.contract

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AiAngleSmoothRuntimeBridgeTest {

    @Test
    fun `missing provider fails instead of silently disabling the configured model`() {
        AiAngleSmoothRuntimeBridge.withProviderForTest(null) {
            assertFailsWith<IllegalStateException> {
                AiAngleSmoothRuntimeBridge.isInitialized
            }
        }
    }

    @Test
    fun `provider exposes model identity prediction and reload notification`() {
        val input = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)
        val output = floatArrayOf(7f, 8f)
        val model = RecordingModel("21KC11KP", output)
        val provider = RecordingProvider(model)

        AiAngleSmoothRuntimeBridge.withProviderForTest(provider) {
            assertTrue(AiAngleSmoothRuntimeBridge.isInitialized)
            assertEquals("21KC11KP", AiAngleSmoothRuntimeBridge.activeModelName)
            assertSame(model, AiAngleSmoothRuntimeBridge.models.single())
            assertContentEquals(output, AiAngleSmoothRuntimeBridge.models.single().predict(input))

            var reloads = 0
            AiAngleSmoothRuntimeBridge.onModelsChanged { reloads++ }
            provider.notifyModelsChanged()
            assertEquals(1, reloads)
        }

        assertSame(input, model.receivedInput)
    }

    @Test
    fun `test provider override restores the previous provider after failure`() {
        val original = RecordingProvider(RecordingModel("Original", floatArrayOf()))
        val replacement = RecordingProvider(RecordingModel("Replacement", floatArrayOf()))

        AiAngleSmoothRuntimeBridge.withProviderForTest(original) {
            assertFailsWith<ExpectedFailure> {
                AiAngleSmoothRuntimeBridge.withProviderForTest(replacement) {
                    assertEquals("Replacement", AiAngleSmoothRuntimeBridge.activeModelName)
                    throw ExpectedFailure()
                }
            }

            assertEquals("Original", AiAngleSmoothRuntimeBridge.activeModelName)
            assertFalse(AiAngleSmoothRuntimeBridge.models.isEmpty())
        }
    }

    private class RecordingModel(
        override val name: String,
        private val output: FloatArray,
    ) : AiAngleSmoothModel {
        var receivedInput: FloatArray? = null

        override fun predict(input: FloatArray): FloatArray {
            receivedInput = input
            return output
        }
    }

    private class RecordingProvider(
        model: AiAngleSmoothModel,
    ) : AiAngleSmoothRuntimeProvider {
        private var listener: (() -> Unit)? = null

        override val isInitialized = true
        override val models = listOf(model)
        override val activeModelName = model.name

        override fun onModelsChanged(listener: () -> Unit) {
            this.listener = listener
        }

        fun notifyModelsChanged() = checkNotNull(listener).invoke()
    }

    private class ExpectedFailure : RuntimeException()
}
