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

import kotlin.test.Test
import kotlin.test.assertEquals

class ProvidedLibrariesTest {
    @Test
    fun `provided library coordinates preserve the complete historical exclusion contract`() {
        val expected = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains.kotlin:kotlin-reflect",
            "org.jetbrains.kotlinx:atomicfu",
            "org.jetbrains.kotlinx:kotlinx-datetime",
            "org.jetbrains.kotlinx:kotlinx-io-core",
            "org.jetbrains.kotlinx:kotlinx-io-bytestring",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "org.jetbrains.kotlinx:kotlinx-serialization-cbor",
            "org.jetbrains.kotlinx:kotlinx-serialization-core",
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
            "it.unimi.dsi:fastutil",
            "com.google.guava:guava",
            "com.google.code.gson:gson",
            "net.java.dev.jna:jna",
            "commons-codec:commons-codec",
            "commons-io:commons-io",
            "org.apache.commons:commons-compress",
            "org.apache.commons:commons-lang3",
            "org.apache.logging.log4j:log4j-core",
            "org.apache.logging.log4j:log4j-api",
            "org.apache.logging.log4j:log4j-slf4j-impl",
            "org.slf4j:slf4j-api",
            "com.mojang:authlib",
            "org.lwjgl:lwjgl",
            "io.netty:netty-buffer",
            "io.netty:netty-codec",
            "io.netty:netty-codec-base",
            "io.netty:netty-codec-compression",
            "io.netty:netty-codec-http",
            "io.netty:netty-common",
            "io.netty:netty-handler",
            "io.netty:netty-resolver",
            "io.netty:netty-transport",
            "io.netty:netty-transport-classes-epoll",
            "io.netty:netty-transport-classes-kqueue",
            "io.netty:netty-transport-native-epoll",
            "io.netty:netty-transport-native-kqueue",
            "io.netty:netty-transport-native-unix-common",
        )

        assertEquals(expected, PROVIDED_LIBRARIES.map(ProvidedLibrary::coordinate))
    }
}
