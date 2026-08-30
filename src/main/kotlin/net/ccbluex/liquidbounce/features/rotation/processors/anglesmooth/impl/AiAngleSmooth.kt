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
package net.ccbluex.liquidbounce.features.rotation.processors.anglesmooth.impl

import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.deeplearn.data.CombatSample
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.rotation.contract.AiAngleSmoothModel
import net.ccbluex.liquidbounce.features.rotation.contract.AiAngleSmoothRuntimeBridge
import net.ccbluex.liquidbounce.features.rotation.processors.anglesmooth.AngleSmooth
import net.ccbluex.liquidbounce.features.rotation.processors.anglesmooth.NoneAngleSmooth
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.ccbluex.liquidbounce.utils.entity.lastRotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

/**
 * Record using
 * - [net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.DebugCombatRecorder]
 * - [net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.DebugCombatTrainerRecorder]
 * and then train a model - after that you will be able to use it with
 * [net.ccbluex.liquidbounce.features.rotation.processors.anglesmooth.impl.AiAngleSmooth].
 */
class AiAngleSmooth(
    parent: ModeValueGroup<*>,
    val fallback: AngleSmooth
) : AngleSmooth("AI", parent, listOf("Minarai")) {

    private val choices = modes<DeepLearningModelMode>("Model", 0) { local ->
        AiAngleSmoothRuntimeBridge.onModelsChanged { synchronizeModelChoices(local) }
        modelChoices(local)
    }

    private class DeepLearningModelMode(
        override val parent: ModeValueGroup<*>,
        private val model: AiAngleSmoothModel,
    ) : Mode(model.name) {
        fun predict(input: FloatArray): FloatArray = model.predict(input)
    }

    private class OutputMultiplier : ValueGroup("OutputMultiplier") {
        val yawMultiplier by float("Yaw", 1.5f, 0.5f..2f)
        val pitchMultiplier by float("Pitch", 1f, 0.5f..2f)
    }

    private val correctionMode = modes(this, "Correction") {
        arrayOf(
            /**
             * Works best with the model, as it allows for the most natural movement.
             */
            InterpolationAngleSmooth(it, 2..5, 2..5, 95..100),
            /**
             * Not recommended to use this one, as it completely eliminates any acceleration
             * effects from the model.
             */
            LinearAngleSmooth(it,
                horizontalTurnSpeed = 5f..5f,
                verticalTurnSpeed = 5f..5f
            ),
            NoneAngleSmooth(it)
        )
    }

    private val outputMultiplier = tree(OutputMultiplier())

    companion object {
        private const val UNSUPPORTED_NOTIFICATION_TIME = 5000L
        private val notificationChronometer = Chronometer()
    }

    override fun process(
        rotationTarget: RotationTarget,
        currentRotation: Rotation,
        targetRotation: Rotation
    ): Rotation {
        fallbackWhenUnavailable(rotationTarget, currentRotation, targetRotation)?.let { return it }

        val input = createModelInput(rotationTarget, currentRotation, targetRotation)
        val (output, time) = predict(input)
            ?: return fallback.process(rotationTarget, currentRotation, targetRotation)
        DebugParameterSink.publish(this, "Output [0]") { output[0] }
        DebugParameterSink.publish(this, "Output [1]") { output[1] }
        DebugParameterSink.publish(this, "Time") { "${time.toString(DurationUnit.MILLISECONDS, 2)} ms" }

        val modelOutput = Rotation(
            currentRotation.yaw + output[0] * outputMultiplier.yawMultiplier,
            currentRotation.pitch + output[1] * outputMultiplier.pitchMultiplier
        )

        return correctionMode.activeMode.process(rotationTarget, modelOutput, targetRotation)
    }

    private fun createModelInput(
        rotationTarget: RotationTarget,
        currentRotation: Rotation,
        targetRotation: Rotation,
    ): FloatArray {
        val entity = rotationTarget.entity as? LivingEntity
        val prevRotation = RotationManager.previousRotation ?: player.lastRotation
        val totalDelta = currentRotation.rotationDeltaTo(targetRotation)
        val velocityDelta = prevRotation.rotationDeltaTo(currentRotation)

        DebugParameterSink.publish(this, "DeltaYaw") { totalDelta.deltaYaw }
        DebugParameterSink.publish(this, "DeltaPitch") { totalDelta.deltaPitch }

        return CombatSample(
            currentVector = currentRotation.directionVector,
            previousVector = prevRotation.directionVector,
            targetVector = targetRotation.directionVector,
            velocityDelta = velocityDelta.toVec2f(),

            playerDiff = player.position().subtract(player.lastPos),
            targetDiff = entity?.let { entity.position().subtract(entity.lastPos) } ?: Vec3.ZERO,

            hurtTime = entity?.let { entity.hurtTime } ?: 10,
            distance = entity?.let { player.squaredBoxedDistanceTo(entity).toFloat() } ?: 3f,
            age = 0
        ).asInput
    }

    private fun modelChoices(parent: ModeValueGroup<*>): Array<DeepLearningModelMode> =
        AiAngleSmoothRuntimeBridge.models.map { model -> DeepLearningModelMode(parent, model) }.toTypedArray()

    private fun synchronizeModelChoices(local: ModeValueGroup<DeepLearningModelMode>) {
        runCatching {
            val activeModelName = local.activeMode.tag
            local.modes = modelChoices(local).toMutableList()
            val nextModelName = local.modes.firstOrNull { model -> model.tag == activeModelName }
                ?.tag ?: AiAngleSmoothRuntimeBridge.activeModelName
            local.setByString(nextModelName)
        }.onFailure { error ->
            logger.error("Failed to sync AI model selection after model reload.", error)
        }
    }

    private fun fallbackWhenUnavailable(
        rotationTarget: RotationTarget,
        currentRotation: Rotation,
        targetRotation: Rotation,
    ): Rotation? {
        if (AiAngleSmoothRuntimeBridge.isInitialized) {
            return null
        }
        if (notificationChronometer.hasElapsed(UNSUPPORTED_NOTIFICATION_TIME)) {
            chat(markAsError(translation("liquidbounce.unsupportedDeepLearning")))
            chat(markAsError(translation(
                "liquidbounce.rotationSystem.angleSmooth.ai.fallback",
                fallback.name
            )))
            notificationChronometer.reset()
        }
        return fallback.process(rotationTarget, currentRotation, targetRotation)
    }

    private fun predict(input: FloatArray) = runCatching {
        measureTimedValue { choices.activeMode.predict(input) }
    }.getOrNull()?.takeIf { (output, _) ->
        output.size >= 2 && output[0].isFinite() && output[1].isFinite()
    }

    override fun calculateTicks(
        currentRotation: Rotation,
        targetRotation: Rotation
    ): Int {
        // TODO: Implement correctly
        return correctionMode.activeMode.calculateTicks(currentRotation, targetRotation)
    }

}
