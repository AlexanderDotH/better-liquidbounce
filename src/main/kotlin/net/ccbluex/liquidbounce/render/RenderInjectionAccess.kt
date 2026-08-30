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
package net.ccbluex.liquidbounce.render

import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup

fun interface RenderSetupFactory {
    fun copyWithOutputTarget(setup: RenderSetup, outputTarget: OutputTarget): RenderSetup
}

object RenderInjectionAccess {

    @Volatile
    private var renderSetupFactory: RenderSetupFactory? = null

    @JvmStatic
    @Synchronized
    fun install(factory: RenderSetupFactory) {
        check(renderSetupFactory == null) { "RenderSetup injection adapter is already installed" }
        renderSetupFactory = factory
    }

    fun copyWithOutputTarget(setup: RenderSetup?, outputTarget: OutputTarget?): RenderSetup {
        val factory = renderSetupFactory ?: error("RenderSetup injection adapter is not installed")
        return factory.copyWithOutputTarget(requireNotNull(setup), requireNotNull(outputTarget))
    }

    @Synchronized
    internal fun <T> withRenderSetupFactoryForTest(candidate: RenderSetupFactory?, block: () -> T): T {
        val previous = renderSetupFactory
        renderSetupFactory = candidate
        return try {
            block()
        } finally {
            renderSetupFactory = previous
        }
    }
}
