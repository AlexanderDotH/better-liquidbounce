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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.api.interceptors.CacheBlacklistInterceptor
import net.ccbluex.liquidbounce.api.interceptors.DefaultHeaderInterceptor
import net.ccbluex.liquidbounce.api.thirdparty.mojang.MojangApiClient
import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object HttpClient {

    @JvmField
    val DEFAULT_AGENT = "${ClientBuildMetadata.NAME}/${ClientBuildMetadata.version}" +
        " (${ClientBuildMetadata.commit}, ${ClientBuildMetadata.branch}, " +
        "${if (ClientBuildMetadata.IN_DEVELOPMENT) "dev" else "release"}, ${System.getProperty("os.name")})"

    /**
     * Unfortunately, Lunar Client uses OkHttp 4.12.0 which does not have [Headers.EMPTY]
     */
    @Deprecated("Use Headers.EMPTY instead when Lunar Client updates OkHttp to 5.10 or newer.")
    @JvmField
    val EMPTY_HEADERS = Headers.Builder().build()

    object MediaTypes {
        @JvmField
        val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()

        @JvmField
        val JSON = "application/json; charset=utf-8".toMediaType()

        @JvmField
        val FORM = "application/x-www-form-urlencoded".toMediaType()

        @JvmField
        val IMAGE_PNG = "image/png".toMediaType()

        @JvmField
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }

    private val defaultClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher(
                Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("OkHttpClient Dispatcher ", 0L).factory()
                )
            )
        )
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true).apply {
            try {
                val file = File(
                    System.getProperty("java.io.tmpdir"),
                    "${ClientBuildMetadata.NAME.lowercase(Locale.ROOT)}_http_cache",
                )
                file.mkdirs()
                cache(Cache(file, 128L shl 20))
            } catch (e: IOException) {
                logger.error("Failed to initialize cache directory for HTTP client", e)
            }
        }
        .addInterceptor(CacheBlacklistInterceptor(setOf("localhost", "127.0.0.1")))
        .addInterceptor(DefaultHeaderInterceptor("User-Agent", DEFAULT_AGENT, skipIfExists = true))
        .proxy(java.net.Proxy.NO_PROXY)
        .build()

    internal val browserClient: OkHttpClient
        get() = defaultClient

    /**
     * This interceptor rejects all non-2xx responses
     */
    private val clientHttpApiInterceptor = Interceptor { chain ->
        val request = chain.request()
        try {
            val response = chain.proceed(request)

            if (response.isSuccessful) {
                response
            } else {
                // Response is not successful (code is not 2xx)
                throw HttpException(
                    enumValueOf(request.method),
                    request.url.toString(), response.code, response.body.string()
                )
            }
        } catch (e: IOException) {
            // Failed to request
            logger.error("Failed to execute request ${request.method} ${request.url})", e)
            throw e
        }
    }

    /**
     * API client
     */
    @get:JvmStatic
    val client = defaultClient.newBuilder()
        .addInterceptor(clientHttpApiInterceptor)
        .build()

    @get:JvmStatic
    val mojangApiClient = MojangApiClient.Builder()
        .gson(interopGson)
        .httpClient(this.defaultClient)
        .tokenProvider { mc.user.accessToken }
        .build()

    @Suppress("LongParameterList")
    suspend fun request(
        url: String,
        method: HttpMethod,
        agent: String = DEFAULT_AGENT,
        headers: Headers.Builder.() -> Unit = {},
        body: RequestBody? = null,
        progressListener: HttpProgressListener? = null
    ): Response {
        val request = Request.Builder()
            .url(url)
            .method(method.name, body)
            .headers(Headers.Builder().apply(headers).build())
            .header("User-Agent", agent)
            .build()

        return if (progressListener == null) {
            client.newCall(request).executeAsync()
        } else {
            client.newBuilder()
                .addNetworkInterceptor(HttpProgressInterceptor(progressListener))
                .build()
                .newCall(request).executeAsync()
        }
    }

    suspend fun download(
        url: String,
        file: File,
        agent: String = DEFAULT_AGENT,
        progressListener: HttpProgressListener? = null
    ) = withContext(Dispatchers.IO) {
        request(url, HttpMethod.GET, agent, progressListener = progressListener).toFile(file)
    }

    // For Java and JS
    @JvmStatic
    fun Call.sendAsync(): CompletableFuture<Response> {
        val future = CompletableFuture<Response>().exceptionally { throwable ->
            if (throwable is CancellationException) this.cancel()
            throw throwable
        }
        this.enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (!future.complete(response)) response.close()
                }

                override fun onFailure(call: Call, e: IOException) {
                    future.completeExceptionally(e)
                }
            }
        )
        return future
    }

}
