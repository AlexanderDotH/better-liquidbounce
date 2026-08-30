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

import net.minecraft.resources.Identifier
import java.io.InputStream

internal const val RENDER_CLIENT_NAME = "LiquidBounce"
private const val RENDER_NAMESPACE = "liquidbounce"
private const val RESOURCE_ROOT = "/resources/liquidbounce/"

internal fun renderIdentifier(path: String): Identifier =
    Identifier.fromNamespaceAndPath(RENDER_NAMESPACE, path)

internal fun renderResource(path: String): InputStream =
    RenderCoreEnvironment::class.java.getResourceAsStream("$RESOURCE_ROOT$path")
        ?: throw IllegalArgumentException("Resource $path not found")

internal fun renderResourceToString(path: String): String =
    renderResource(path).use { it.bufferedReader().readText() }

private object RenderCoreEnvironment
