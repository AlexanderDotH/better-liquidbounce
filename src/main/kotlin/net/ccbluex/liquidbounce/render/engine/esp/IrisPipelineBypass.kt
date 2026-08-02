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

package net.ccbluex.liquidbounce.render.engine.esp

import java.lang.reflect.Field

/**
 * Prevents Iris from replacing LiquidBounce's mask and post-process shaders with a shader-pack program.
 *
 * Iris intentionally exposes this flag for render paths that must keep their own pipeline. Reflection keeps
 * Iris an optional runtime dependency and confines the version-sensitive access to this compatibility seam.
 */
internal object IrisPipelineBypass {

    private val bypass = StaticBooleanBypass(
        runCatching {
            Class.forName(
                "net.irisshaders.iris.vertices.ImmediateState",
                false,
                IrisPipelineBypass::class.java.classLoader,
            ).getField("bypass")
        }.getOrNull()
    )

    fun <T> run(block: () -> T): T = bypass.run(block)
}

internal class StaticBooleanBypass(private val field: Field?) {

    fun <T> run(block: () -> T): T {
        val field = field ?: return block()
        val previous = runCatching { field.getBoolean(null) }.getOrElse { return block() }

        if (previous) {
            return block()
        }

        runCatching { field.setBoolean(null, true) }.getOrElse { return block() }

        return try {
            block()
        } finally {
            field.setBoolean(null, false)
        }
    }
}
