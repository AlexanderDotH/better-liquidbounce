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

@file:Suppress("NOTHING_TO_INLINE")

@file:JvmName("RenderExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.render.buffer

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.GpuDevice
import kotlinx.coroutines.asExecutor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.gpuDevice
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.io.ensurePngOrConvertJpeg
import okio.buffer
import okio.source
import java.nio.ByteBuffer
import java.util.function.Supplier

@JvmInline
value class KStd140SizeCalculator(val j: Std140SizeCalculator) {
    inline val float: Unit
        get() {
            j.putFloat()
        }
    inline val int: Unit
        get() {
            j.putInt()
        }
    inline val vec2: Unit
        get() {
            j.putVec2()
        }
    inline val ivec2: Unit
        get() {
            j.putIVec2()
        }
    inline val vec3: Unit
        get() {
            j.putVec3()
        }
    inline val ivec3: Unit
        get() {
            j.putIVec3()
        }
    inline val vec4: Unit
        get() {
            j.putVec4()
        }
    inline val ivec4: Unit
        get() {
            j.putIVec4()
        }
    inline val mat4f: Unit
        get() {
            j.putMat4f()
        }

    inline fun align(alignedSize: Int) {
        j.align(alignedSize)
    }

    inline operator fun Unit.plus(other: Unit) {
        // NOOP
    }

    inline fun get() = j.get()
}

inline fun std140Size(block: KStd140SizeCalculator.() -> Unit): Int =
    KStd140SizeCalculator(Std140SizeCalculator()).apply(block).get()

inline fun GpuDevice.createUbo(
    labelGetter: Supplier<String>? = null,
    std140Size: KStd140SizeCalculator.() -> Unit,
): GpuBuffer =
    createBuffer(
        labelGetter,
        GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_MAP_WRITE,
        std140Size(std140Size).toLong()
    )

inline fun ByteBuffer.writeStd140(action: Std140Builder.() -> Unit) {
    Std140Builder.intoBuffer(this).apply(action)
}

inline fun GpuBufferSlice.writeStd140(action: Std140Builder.() -> Unit): GpuBufferSlice =
    this.mapBuffer(read = false, write = true).use {
        it.data().writeStd140(action)

        this
    }

inline fun Std140Builder.putVec4(color: Color4b): Std140Builder =
    putVec4(color.r / 255f, color.g / 255f, color.b / 255f, color.a / 255f)
