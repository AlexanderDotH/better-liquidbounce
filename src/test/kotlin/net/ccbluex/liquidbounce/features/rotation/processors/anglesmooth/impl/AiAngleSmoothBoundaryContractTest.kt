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
package net.ccbluex.liquidbounce.features.rotation.processors.anglesmooth.impl

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAngleSmoothBoundaryContractTest {

    @Test
    fun `angle smooth implementations depend on the runtime contract and neutral debug sink`() {
        val ai = source("features/rotation/processors/anglesmooth/impl/AiAngleSmooth.kt")
        val interpolation = source(
            "features/rotation/processors/anglesmooth/impl/InterpolationAngleSmooth.kt"
        )

        listOf(ai, interpolation).forEach { implementation ->
            assertFalse("features.module.modules.render" in implementation)
        }
        assertFalse("deeplearn.DeepLearningEngine" in ai)
        assertFalse("deeplearn.ModelManager" in ai)
        assertFalse("deeplearn.models" in ai)
        assertTrue("features.rotation.contract.AiAngleSmoothRuntimeBridge" in ai)
        assertTrue("common.debug.DebugParameterSink" in ai)
        assertTrue("common.debug.DebugParameterSink" in interpolation)
    }

    @Test
    fun `model input prediction validation and correction retain their order`() {
        val ai = source("features/rotation/processors/anglesmooth/impl/AiAngleSmooth.kt")

        assertOrdered(
            ai,
            "fallbackWhenUnavailable",
            "createModelInput(rotationTarget, currentRotation, targetRotation)",
            "predict(input)",
            "DebugParameterSink.publish(this, \"Output [0]\")",
            "currentRotation.yaw + output[0] * outputMultiplier.yawMultiplier",
            "correctionMode.activeMode.process",
        )
        assertOrdered(
            ai.substringAfter("private fun createModelInput("),
            "rotationTarget.entity as? LivingEntity",
            "RotationManager.previousRotation ?: player.lastRotation",
            "currentRotation.rotationDeltaTo(targetRotation)",
            "prevRotation.rotationDeltaTo(currentRotation)",
            "DebugParameterSink.publish(this, \"DeltaYaw\")",
            "CombatSample(",
            "currentVector = currentRotation.directionVector",
            "previousVector = prevRotation.directionVector",
            "targetVector = targetRotation.directionVector",
            "velocityDelta = velocityDelta.toVec2f()",
            "playerDiff = player.position().subtract(player.lastPos)",
            "targetDiff = entity?.let { entity.position().subtract(entity.lastPos) } ?: Vec3.ZERO",
            "hurtTime = entity?.let { entity.hurtTime } ?: 10",
            "distance = entity?.let { player.squaredBoxedDistanceTo(entity).toFloat() } ?: 3f",
            "age = 0",
            ").asInput",
        )
        assertOrdered(
            ai.substringAfter("private fun predict(input: FloatArray)"),
            "measureTimedValue { choices.activeMode.predict(input) }",
            "output.size >= 2 && output[0].isFinite() && output[1].isFinite()",
        )
    }

    @Test
    fun `deep learning adapter is installed before feature construction`() {
        val adapter = source("deeplearn/AiAngleSmoothDeepLearningAdapter.kt")
        val initializer = source("bootstrap/liquidbounce/ClientInitializer.kt")

        assertTrue("DeepLearningEngine.isInitialized" in adapter)
        assertTrue("ModelManager.models.modes.map(::DeepLearningModel)" in adapter)
        assertTrue("ModelManager.models.onChanged { listener() }" in adapter)
        assertTrue("model.predict(input)" in adapter)
        assertOrdered(
            initializer,
            "AiAngleSmoothDeepLearningAdapter.install()",
            "initializeFeatures()",
        )
    }

    private fun source(relativePath: String): String = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/$relativePath"
    ).readText()

    private fun assertOrdered(source: String, vararg tokens: String) {
        var previousIndex = -1
        tokens.forEach { token ->
            val index = source.indexOf(token, previousIndex + 1)
            assertTrue(index > previousIndex, "Missing or reordered token: $token")
            previousIndex = index
        }
    }
}
