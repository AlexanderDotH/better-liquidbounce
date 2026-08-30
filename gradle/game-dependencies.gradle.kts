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

import org.gradle.api.artifacts.VersionCatalogsExtension

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun library(alias: String) = catalog.findLibrary(alias).get()

val baritoneApiFabric = "baritone.vendor:baritone-api-fabric:1.15.0-10-g2991d921"
val litematicaRuntime = configurations.getByName("litematicaIntegrationTestRuntime")

dependencies {
    add("minecraft", library("minecraft"))
    add("api", library("fabric-loader"))
    add("api", library("fabric-api"))
    add("api", library("fabric-kotlin"))

    // Optional integrations must remain outside the runtime and nested-JAR configurations.
    add("compileOnly", "maven.modrinth:DistantHorizonsApi:7.0.0")
    add("compileOnly", library("litematica"))
    add("compileOnly", library("malilib"))
    add(litematicaRuntime.name, library("litematica"))
    add(litematicaRuntime.name, library("malilib"))

    // Keep Baritone intact so its Fabric metadata, mixins, and reflection provider survive packaging.
    add("compileOnly", baritoneApiFabric)
    add("include", baritoneApiFabric)

    add("api", library("modmenu"))
    add("api", library("sodium"))
    add("api", library("lithium"))
    add("runtimeOnly", library("immediatelyFast"))
    add("runtimeOnly", library("iris"))
    add("api", library("vfp-api"))
    add("runtimeOnly", library("vfp"))
    add("api", library("exploitPreventer-api"))
    add("runtimeOnly", library("exploitPreventer"))

    add("jij", library("minecraftauth"))
    add("jij", library("thealtening"))
    add("jij", catalog.findBundle("retrofit").get())
    add("jij", library("lwjgl-egl"))

    add("api", library("mcef"))
    add("include", library("mcef"))

    add("jij", library("ktor-server-core"))
    add("jij", library("ktor-server-netty"))
    add("jij", library("ktor-server-websockets"))
    add("jij", library("ktor-server-sse"))
    add("jij", library("ktor-server-cors"))
    add("jij", library("ktor-server-compression"))
    add("jij", library("ktor-server-content-negotiation"))
    add("jij", library("ktor-server-status-pages"))
    add("jij", library("ktor-serialization-gson"))
}
