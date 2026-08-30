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

package net.ccbluex.liquidbounce.buildsrc.dependencies

internal data class ProvidedLibrary(val group: String, val module: String) {
    val coordinate = "$group:$module"
}

internal val PROVIDED_LIBRARIES = listOf(
    ProvidedLibrary("org.jetbrains.kotlin", "kotlin-stdlib"),
    ProvidedLibrary("org.jetbrains.kotlin", "kotlin-reflect"),
    ProvidedLibrary("org.jetbrains.kotlinx", "atomicfu"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-datetime"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-io-core"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-io-bytestring"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-coroutines-core"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-serialization-cbor"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-serialization-core"),
    ProvidedLibrary("org.jetbrains.kotlinx", "kotlinx-serialization-json"),
    ProvidedLibrary("it.unimi.dsi", "fastutil"),
    ProvidedLibrary("com.google.guava", "guava"),
    ProvidedLibrary("com.google.code.gson", "gson"),
    ProvidedLibrary("net.java.dev.jna", "jna"),
    ProvidedLibrary("commons-codec", "commons-codec"),
    ProvidedLibrary("commons-io", "commons-io"),
    ProvidedLibrary("org.apache.commons", "commons-compress"),
    ProvidedLibrary("org.apache.commons", "commons-lang3"),
    ProvidedLibrary("org.apache.logging.log4j", "log4j-core"),
    ProvidedLibrary("org.apache.logging.log4j", "log4j-api"),
    ProvidedLibrary("org.apache.logging.log4j", "log4j-slf4j-impl"),
    ProvidedLibrary("org.slf4j", "slf4j-api"),
    ProvidedLibrary("com.mojang", "authlib"),
    ProvidedLibrary("org.lwjgl", "lwjgl"),
    ProvidedLibrary("io.netty", "netty-buffer"),
    ProvidedLibrary("io.netty", "netty-codec"),
    ProvidedLibrary("io.netty", "netty-codec-base"),
    ProvidedLibrary("io.netty", "netty-codec-compression"),
    ProvidedLibrary("io.netty", "netty-codec-http"),
    ProvidedLibrary("io.netty", "netty-common"),
    ProvidedLibrary("io.netty", "netty-handler"),
    ProvidedLibrary("io.netty", "netty-resolver"),
    ProvidedLibrary("io.netty", "netty-transport"),
    ProvidedLibrary("io.netty", "netty-transport-classes-epoll"),
    ProvidedLibrary("io.netty", "netty-transport-classes-kqueue"),
    ProvidedLibrary("io.netty", "netty-transport-native-epoll"),
    ProvidedLibrary("io.netty", "netty-transport-native-kqueue"),
    ProvidedLibrary("io.netty", "netty-transport-native-unix-common"),
)
