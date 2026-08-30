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
@file:JvmName("HttpClientKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.api.core

import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.NativeImage
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.utils.render.readNativeImage
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import okio.sink
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.lang.reflect.Type

@PublishedApi
internal fun <T> readInteropJson(reader: Reader, type: Type): T =
    interopGson.fromJson(reader, type)

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD
}

/**
 * Parse body from [Response].
 *
 * If [T] is one of following types, it should be closed after using:
 * [InputStream] / [BufferedSource] / [Reader]
 */
inline fun <reified T> Response.parse(): T {
    return when (T::class.java) {
        String::class.java -> body.string() as T
        Unit::class.java -> close() as T
        InputStream::class.java -> body.byteStream() as T
        BufferedSource::class.java -> body.source() as T
        Reader::class.java -> body.charStream() as T
        NativeImage::class.java -> body.source().readNativeImage() as T
        else -> body.charStream().use { reader ->
            readInteropJson(reader, object : TypeToken<T>() {}.type)
        }
    }
}

/**
 * Read all UTF-8 lines from [BufferedSource] as an [Iterator].
 *
 * When there are no more lines to read, the source is closed automatically.
 */
fun BufferedSource.utf8Lines(): Iterator<String> =
    object : AbstractIterator<String>() {
        override fun computeNext() {
            val nextLine = readUtf8Line()
            if (nextLine != null) {
                setNext(nextLine)
            } else {
                close()
                done()
            }
        }
    }

/**
 * Save response body to file.
 */
fun Response.toFile(file: File) = use { response ->
    file.sink().use(response.body.source()::readAll)
}

fun String.asForm() = toRequestBody(HttpClient.MediaTypes.FORM)

class HttpException(val method: HttpMethod, val url: String, val code: Int, val content: String)
    : Exception("${method.name} $url failed with code $code: $content")
