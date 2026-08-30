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

package net.ccbluex.liquidbounce.api.core

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

fun interface HttpProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}

internal class HttpProgressInterceptor(
    private val listener: HttpProgressListener,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder().body(ProgressResponseBody(response.body, listener)).build()
    }
}

private class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val listener: HttpProgressListener,
) : ResponseBody() {
    private val bufferedSource by lazy { progressSource(delegate.source()).buffer() }

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource = bufferedSource

    private fun progressSource(source: Source) = object : ForwardingSource(source) {
        private var totalBytesRead = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            totalBytesRead += if (bytesRead == -1L) 0L else bytesRead
            listener.update(totalBytesRead, this@ProgressResponseBody.delegate.contentLength(), bytesRead == -1L)
            return bytesRead
        }
    }
}
