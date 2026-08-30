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

internal data class TrainingDataset(val features: FloatArray, val labels: FloatArray)

internal fun prepareTrainingDataset(samples: List<TrainingSample>): TrainingDataset {
    require(samples.isNotEmpty()) { "At least one sample is required" }
    val inputSize = samples.first().inputSize
    val outputSize = samples.first().outputSize
    require(inputSize > 0 && outputSize > 0) { "Sample input and output sizes must be positive" }
    val features = FloatArray(Math.multiplyExact(samples.size, inputSize))
    val labels = FloatArray(Math.multiplyExact(samples.size, outputSize))
    var featureIndex = 0
    var labelIndex = 0
    for (sample in samples) {
        require(sample.inputSize == inputSize && sample.outputSize == outputSize) {
            "All samples must have the same input and output sizes"
        }
        val nextFeatureIndex = sample.fillAsInput(features, featureIndex)
        val nextLabelIndex = sample.fillAsOutput(labels, labelIndex)
        check(nextFeatureIndex == featureIndex + inputSize) { "Sample wrote an unexpected number of inputs" }
        check(nextLabelIndex == labelIndex + outputSize) { "Sample wrote an unexpected number of outputs" }
        featureIndex = nextFeatureIndex
        labelIndex = nextLabelIndex
    }
    return TrainingDataset(features, labels)
}
