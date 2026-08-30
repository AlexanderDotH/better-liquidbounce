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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.deeplearn.ModelManager
import net.ccbluex.liquidbounce.deeplearn.ModelManager.models
import net.ccbluex.liquidbounce.deeplearn.data.CombatSample
import net.ccbluex.liquidbounce.deeplearn.models.TwoDimensionalRegressionModel
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.kotlin.MinecraftDispatcher
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

internal suspend fun trainModel(
    command: Command,
    name: String,
    model: TwoDimensionalRegressionModel? = null,
): Unit = try {
    val (samples, sampleTime) = measureTimedValue {
        CombatSample.parse(
            ModelCommandIntegrationBridge.combatRecorderFolder(),
            ModelCommandIntegrationBridge.trainerRecorderFolder(),
        )
    }
    if (samples.isEmpty()) {
        chat(markAsError(command.result("noSamples")))
        return
    }
    chat(command.result("samplesLoaded", samples.size, sampleTime.toString(DurationUnit.SECONDS, decimals = 2)))
    val (dataset, datasetTime) = measureTimedValue { prepareTrainingDataset(samples) }
    chat(command.result("preparedData", datasetTime.toString(DurationUnit.SECONDS, decimals = 2)))
    val trainingTime = measureTime { trainAndActivateModel(name, model, dataset) }
    chat(command.result("trainingEnd", name, trainingTime.toString(DurationUnit.MINUTES, decimals = 2)))
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    chat(markAsError(command.result("trainingFailed", exception.localizedMessage)))
}

private suspend fun trainAndActivateModel(
    name: String,
    model: TwoDimensionalRegressionModel?,
    dataset: TrainingDataset,
) {
    TwoDimensionalRegressionModel(name, models).use { candidate ->
        if (model != null) {
            candidate.load(model.name)
        }
        candidate.train(dataset.features, dataset.labels)
        candidate.save()
    }
    ModelManager.reload()
    withContext(MinecraftDispatcher) {
        models.setByString(name)
        ModelCommandIntegrationBridge.syncClickGui()
    }
}
