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

package net.ccbluex.liquidbounce.buildsrc.contributors

import groovy.json.JsonSlurper
import org.gradle.api.logging.Logger
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

internal object GitHubContributors {
    private const val PER_PAGE = 100
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build()

    fun fetch(owner: String, repository: String, logger: Logger): List<String> = try {
        val baseUrl = "https://api.github.com/repos/$owner/$repository/contributors"
        val token = System.getenv("GITHUB_TOKEN")
        val pageCount = pageCount(baseUrl, token, logger)
        (1..pageCount).map { page -> fetchPage(baseUrl, page, token, owner, repository, logger) }
            .flatMapTo(ArrayList(PER_PAGE * pageCount), CompletableFuture<List<String>>::get)
            .also { logger.info("Successfully collected ${it.size} contributors") }
    } catch (exception: Exception) {
        logger.error("Failed to fetch contributors of $owner:$repository", exception)
        emptyList()
    }

    private fun pageCount(baseUrl: String, token: String?, logger: Logger): Int {
        val response = client.send(
            request("$baseUrl?per_page=$PER_PAGE", token).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        if (response.isSuccessful) {
            return GitHubContributorParser.lastPage(response.headers().firstValue("link").orElse(""))
        }
        logger.error("HEAD request to ${response.uri()} failed with status: ${response.statusCode()}")
        return 1
    }

    private fun fetchPage(
        baseUrl: String,
        page: Int,
        token: String?,
        owner: String,
        repository: String,
        logger: Logger,
    ): CompletableFuture<List<String>> {
        val request = request("$baseUrl?per_page=$PER_PAGE&page=$page", token).GET().build()
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply { response ->
            response.body().use { body -> contributorResponse(response, body, owner, repository, logger) }
        }
    }

    private fun contributorResponse(
        response: HttpResponse<InputStream>,
        body: InputStream,
        owner: String,
        repository: String,
        logger: Logger,
    ): List<String> {
        if (!response.isSuccessful) {
            logger.error("Failed to get GitHub API response for $owner:$repository " +
                "(HTTP ${response.statusCode()}): ${body.bufferedReader().readText()}")
            return emptyList()
        }
        return runCatching { GitHubContributorParser.userLogins(body) }.getOrElse { exception ->
            logger.error("Failed to parse GitHub API response for $owner:$repository", exception)
            emptyList()
        }
    }

    private fun request(url: String, token: String?) = HttpRequest.newBuilder()
        .uri(URI(url))
        .timeout(Duration.ofSeconds(10))
        .header("User-Agent", "LiquidBounce-App")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Accept", "application/vnd.github+json")
        .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }

    private inline val HttpResponse<*>.isSuccessful get() = statusCode() in 200..299
}

internal object GitHubContributorParser {
    private val lastPagePattern = Regex("""&page=(\d+)>; rel="last"""")

    fun lastPage(linkHeader: String): Int = lastPagePattern.find(linkHeader)?.groupValues?.get(1)?.toInt() ?: 1

    fun userLogins(input: InputStream): List<String> = (JsonSlurper().parse(input) as? List<*>)
        .orEmpty()
        .mapNotNull { value ->
            val contributor = value as? Map<*, *> ?: return@mapNotNull null
            (contributor["login"] as? String)?.takeIf { contributor["type"] == "User" }
        }
}
