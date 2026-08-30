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

package net.ccbluex.liquidbounce.buildsrc.quality

import groovy.json.JsonSlurper
import net.ccbluex.liquidbounce.buildsrc.quality.config.RatchetJson
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetBaseline
import java.nio.file.Files
import java.nio.file.Path

data class GitQualityState(
    val touchedPaths: Set<String>,
    val referenceBaseline: RatchetBaseline?,
    val currentRevision: String,
) {
    companion object {
        fun load(repositoryRoot: Path, ratchetPath: Path, requestedBaseRevision: String? = null): GitQualityState {
            val git = GitCommands(repositoryRoot)
            val detectedBase = requestedBaseRevision ?: environmentBaseRevision()
            detectedBase?.let { base ->
                require(git.output("rev-parse", "--verify", "$base^{commit}") != null) {
                    "Cannot compare source quality with Git base revision $base"
                }
            }
            val touched = buildSet {
                addAll(git.pathLines("diff", "--name-only", "--diff-filter=ACMR", "--relative"))
                addAll(git.pathLines("diff", "--cached", "--name-only", "--diff-filter=ACMR", "--relative"))
                addAll(git.pathLines("ls-files", "--others", "--exclude-standard"))
                detectedBase?.let { base ->
                    val committed = git.pathLinesOrNull("diff", "--name-only", "--diff-filter=ACMR", "$base...HEAD")
                    requireNotNull(committed) { "Cannot compare source quality with Git base revision $base" }
                    addAll(committed)
                }
            }
            val relativeRatchet = repositoryRoot.toAbsolutePath().normalize()
                .relativize(ratchetPath.toAbsolutePath().normalize()).toString().replace('\\', '/')
            val referenceRevision = detectedBase ?: "HEAD"
            val referenceText = git.output("show", "$referenceRevision:$relativeRatchet")
            return GitQualityState(
                touchedPaths = touched.mapTo(sortedSetOf()) { it.replace('\\', '/') },
                referenceBaseline = referenceText?.let { RatchetJson.parse(it, "$referenceRevision:$relativeRatchet") },
                currentRevision = requireNotNull(git.output("rev-parse", "HEAD")) { "Cannot resolve current Git revision" }.trim(),
            )
        }

        private fun environmentBaseRevision(): String? =
            System.getenv("SOURCE_QUALITY_BASE_SHA")?.takeIf(String::isNotBlank) ?: githubEventBaseRevision()

        private fun githubEventBaseRevision(): String? {
            val eventPath = System.getenv("GITHUB_EVENT_PATH")?.takeIf(String::isNotBlank) ?: return null
            val event = runCatching { JsonSlurper().parseText(Files.readString(Path.of(eventPath))) as? Map<*, *> }.getOrNull()
                ?: return null
            val pullRequest = event["pull_request"] as? Map<*, *>
            val base = pullRequest?.get("base") as? Map<*, *>
            val pullRequestSha = base?.get("sha") as? String
            val pushBefore = event["before"] as? String
            return pullRequestSha?.takeIf(String::isNotBlank)
                ?: pushBefore?.takeIf { it.isNotBlank() && it.any { character -> character != '0' } }
        }
    }
}

private class GitCommands(private val repositoryRoot: Path) {
    fun pathLines(vararg arguments: String): List<String> = pathLinesOrNull(*arguments).orEmpty()

    fun pathLinesOrNull(vararg arguments: String): List<String>? = output(*arguments)
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toList()

    fun output(vararg arguments: String): String? {
        val process = ProcessBuilder(listOf("git", "-C", repositoryRoot.toString()) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        return output.takeIf { process.waitFor() == 0 }
    }
}
