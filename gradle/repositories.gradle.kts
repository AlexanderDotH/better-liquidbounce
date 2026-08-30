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

val jij = configurations.create("jij")
jij.excludeProvidedLibs()

configurations.create("seedFindingUnrelocated") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

configurations.create("litematicaIntegrationTestRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = true
}

allprojects {
    repositories {
        // Loom needs a synthetic module coordinate to preserve the reviewed Baritone Fabric metadata.
        flatDir {
            dirs(rootProject.file("third_party/baritone"))
            content {
                includeGroup("baritone.vendor")
            }
        }
        // relocateSeedFindingJars produces this dependency before Loom resolves its include configuration.
        flatDir {
            dirs(layout.buildDirectory.dir("generated/seedcracker"))
        }
        mavenCentral()
        mavenLocal()
        maven {
            name = "CCBlueX Releases"
            url = uri("https://maven.ccbluex.net/releases")
        }
        maven {
            name = "CCBlueX Snapshots"
            url = uri("https://maven.ccbluex.net/snapshots")
        }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Jitpack"
            url = uri("https://jitpack.io")
        }
        maven {
            name = "ViaVersion"
            url = uri("https://repo.viaversion.com/")
        }
        maven {
            name = "modrinth"
            url = uri("https://api.modrinth.com/maven")
        }
        maven {
            name = "OpenCollab Snapshots"
            url = uri("https://repo.opencollab.dev/maven-snapshots/")
        }
        maven {
            name = "Lenni0451"
            url = uri("https://maven.lenni0451.net/everything")
        }
        maven {
            url = uri("https://maven.shedaniel.me/")
        }
        maven {
            name = "LattiCG"
            url = uri("https://maven.latticg.com/")
        }
        maven {
            name = "SeedFinding Releases"
            url = uri("https://maven.seedfinding.com/")
        }
        maven {
            name = "SeedFinding Snapshots"
            url = uri("https://maven-snapshots.seedfinding.com/")
        }
    }
}
