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

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun library(alias: String) = catalog.findLibrary(alias).get()
fun version(alias: String) = catalog.findVersion(alias).get().requiredVersion

val seedFindingUnrelocated = configurations.getByName("seedFindingUnrelocated")
val seedFindingAliases = listOf(
    "seedfinding-mc-math",
    "seedfinding-mc-seed",
    "seedfinding-mc-core",
    "seedfinding-mc-noise",
    "seedfinding-mc-biome",
    "seedfinding-mc-terrain",
    "seedfinding-mc-feature",
    "seedfinding-mc-reversal",
    "latticg",
)

dependencies {
    fun addSeedFinding(alias: String) {
        val declared = create(library(alias).get())
        require(declared is ModuleDependency) { "SeedFinding dependency must be a module: $alias" }
        declared.isTransitive = false
        seedFindingUnrelocated.dependencies.add(declared)
    }

    add("jij", library("polyglot"))
    add("jij", library("polyglot-js"))
    add("jij", library("polyglot-tools"))
    add("jij", library("djl-api"))
    add("jij", library("djl-pytorch"))
    add("jij", catalog.findBundle("okhttp").get())
    add("jij", library("netty-handler-proxy"))
    add("jij", library("semver4j"))
    add("jij", library("ahocorasick"))
    add("compileOnlyApi", library("fastutil4k-extensionsOnly"))
    add("jij", library("fastutil4k-moreCollections"))
    add("jij", library("discord-ipc"))

    seedFindingAliases.forEach(::addSeedFinding)

    add("testImplementation", "org.jetbrains.kotlin:kotlin-test:${version("kotlin")}")
    add("testImplementation", library("fabric-loader-junit"))
    add("testImplementation", library("kotlinx-coroutines-test"))
    add("testImplementation", "io.ktor:ktor-server-test-host:${version("ktor")}")
    add("testImplementation", "baritone.vendor:baritone-api-fabric:1.15.0-10-g2991d921")
}

val jij = configurations.getByName("jij")
addResolvedDependencies(jij, "compileOnly", "include", "api")

val seedFindingCoordinates = seedFindingAliases.map { alias -> library(alias).get().toString() }
val relocateSeedFindingJars = tasks.register<RelocateSeedFindingJarsTask>("relocateSeedFindingJars") {
    sourceJars.from(seedFindingUnrelocated)
    sourceCoordinates.set(seedFindingCoordinates)
    outputJar.set(layout.buildDirectory.file("generated/seedcracker/seedcracker-seedfinding-26.2.jar"))
}
val relocatedSeedFindingJar = files(relocateSeedFindingJars.flatMap { it.outputJar })

dependencies {
    add("compileOnly", relocatedSeedFindingJar)
    add("include", "net.ccbluex:seedcracker-seedfinding:26.2")
    add("testImplementation", relocatedSeedFindingJar)
}

tasks.matching { it.name in setOf("compileKotlin", "compileTestKotlin", "processIncludeJars") }.configureEach {
    dependsOn(relocateSeedFindingJars)
}
