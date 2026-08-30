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
package net.ccbluex.liquidbounce.common

import java.io.InputStream

fun clientResourceOrNull(path: String): InputStream? =
    ClientResources::class.java.getResourceAsStream("/resources/liquidbounce/$path")

fun clientResource(path: String): InputStream = clientResourceOrNull(path)
    ?: throw IllegalArgumentException("Resource $path not found")

fun clientResourceToString(path: String): String =
    clientResource(path).use { it.bufferedReader().readText() }

private object ClientResources
