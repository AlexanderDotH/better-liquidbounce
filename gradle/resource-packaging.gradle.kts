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

import groovy.json.JsonOutput
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Copy

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun version(alias: String) = catalog.findVersion(alias).get().requiredVersion

tasks.named<Copy>("processResources") {
    dependsOn("buildTheme")
    from("src-theme/dist") {
        into("resources/liquidbounce/themes/liquidbounce")
    }
    from("third_party/baritone/LICENSE") {
        into("META-INF/licenses/baritone")
    }
    from("third_party/baritone/NOTICE.md") {
        into("META-INF/notices/baritone")
    }
    from("third_party/baritone/ORIGIN.md") {
        into("META-INF/notices/baritone")
    }

    val modVersion = providers.gradleProperty("mod_version")
    val minecraftVersion = providers.gradleProperty("mod_mc_version")
    val isGitHubCi = providers.environmentVariable("GITHUB_ACTIONS")
        .map(String::toBoolean)
        .orElse(false)
    val contributors = provider {
        if (!isGitHubCi.get()) {
            logger.lifecycle("Skipping contributor fetch outside GitHub CI")
            "[]"
        } else {
            val values = getContributors("CCBlueX", "LiquidBounce")
            logger.lifecycle("Fetched ${values.size} contributors on GitHub CI")
            JsonOutput.prettyPrint(JsonOutput.toJson(values))
        }
    }
    val replacements = mapOf(
        "version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "fabric_version" to version("fabric-api"),
        "loader_version" to version("fabric-loader"),
        "min_loader_version" to version("fabric-loaderMin"),
        "contributors" to contributors,
        "fabric_kotlin_version" to version("fabric-kotlin"),
        "viafabricplus_version" to version("viafabricplus"),
    )

    replacements.forEach { (key, value) -> inputs.property(key, value) }
    filesMatching("fabric.mod.json") {
        expand(replacements.mapValues { (_, value) -> if (value is org.gradle.api.provider.Provider<*>) value.get() else value })
    }
}
