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

import net.ccbluex.liquidbounce.buildsrc.contributors.GitHubContributors
import net.ccbluex.liquidbounce.buildsrc.dependencies.PROVIDED_LIBRARIES
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.exclude

/**
 * [API Docs](https://docs.github.com/zh/rest/collaborators/collaborators?apiVersion=2022-11-28)
 */
fun Task.getContributors(repoOwner: String, repoName: String): List<String> =
    GitHubContributors.fetch(repoOwner, repoName, logger)

fun Project.addResolvedDependencies(
    from: Configuration,
    vararg toConfigurations: String,
) {
    val resolvedDeps = from.incoming.resolutionResult.allDependencies
        .map { dep ->
            val requested = dep.requested.displayName
            dependencies.create(requested) {
                (this as? ModuleDependency)?.isTransitive = false
            }
        }

    toConfigurations.forEach { configName ->
        configurations.named(configName).configure {
            withDependencies {
                addAll(resolvedDeps)
            }
        }
    }
}

/**
 * Provided by:
 * - Minecraft
 * - Mod dependencies
 */
fun Configuration.excludeProvidedLibs() = apply {
    PROVIDED_LIBRARIES.forEach { library -> exclude(group = library.group, module = library.module) }
}
