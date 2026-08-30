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
package net.ccbluex.liquidbounce.deeplearn

import net.ccbluex.liquidbounce.deeplearn.models.TwoDimensionalRegressionModel
import net.ccbluex.liquidbounce.features.rotation.contract.AiAngleSmoothModel
import net.ccbluex.liquidbounce.features.rotation.contract.AiAngleSmoothRuntimeBridge
import net.ccbluex.liquidbounce.features.rotation.contract.AiAngleSmoothRuntimeProvider

internal object AiAngleSmoothDeepLearningAdapter : AiAngleSmoothRuntimeProvider {

    override val isInitialized: Boolean
        get() = DeepLearningEngine.isInitialized

    override val models: List<AiAngleSmoothModel>
        get() = ModelManager.models.modes.map(::DeepLearningModel)

    override val activeModelName: String
        get() = ModelManager.models.activeMode.tag

    override fun onModelsChanged(listener: () -> Unit) {
        ModelManager.models.onChanged { listener() }
    }

    fun install() = AiAngleSmoothRuntimeBridge.install(this)

    private class DeepLearningModel(
        private val model: TwoDimensionalRegressionModel,
    ) : AiAngleSmoothModel {
        override val name: String
            get() = model.tag

        override fun predict(input: FloatArray): FloatArray = model.predict(input)
    }
}
