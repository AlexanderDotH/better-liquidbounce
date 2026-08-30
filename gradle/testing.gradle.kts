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

import net.ccbluex.liquidbounce.buildsrc.quality.SourceQualityGateTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.support.listFilesOrdered

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

val sourceSets = extensions.getByType<SourceSetContainer>()
val testRuntimeClasspath = configurations.getByName("testRuntimeClasspath")
val disabledFabricMods = arrayOf(
    "immediatelyfast",
    "org_jetbrains_kotlin_kotlin-reflect",
    "org_jetbrains_kotlin_kotlin-stdlib",
    "org_jetbrains_kotlin_kotlin-stdlib-jdk7",
    "org_jetbrains_kotlin_kotlin-stdlib-jdk8",
).joinToString(",")

fun Test.configureFabricTestRuntime() {
    systemProperty("java.awt.headless", "true")
    systemProperty("fabric.debug.disableModIds", disabledFabricMods)
    jvmArgumentProviders.add(
        objects.newInstance<FabricSystemLibrariesArgumentProvider>().apply {
            runtimeClasspath.from(testRuntimeClasspath)
        },
    )
}

val test = tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("litematica-integration")
    }
    configureFabricTestRuntime()
}

val litematicaIntegrationTest = tasks.register<Test>("litematicaIntegrationTest") {
    group = "verification"
    description = "Verifies the adapter against the pinned Litematica and MaLiLib artifacts"
    testClassesDirs = sourceSets.getByName("test").output.classesDirs
    classpath = sourceSets.getByName("test").runtimeClasspath +
        configurations.getByName("litematicaIntegrationTestRuntime")
    useJUnitPlatform {
        includeTags("litematica-integration")
    }
    configureFabricTestRuntime()
    shouldRunAfter(test)
}

tasks.named("check") {
    dependsOn(litematicaIntegrationTest)
}

tasks.register<CompareJsonKeysTask>("verifyI18nJsonKeys") {
    val baselineFileName = "en_us.json"
    group = "verification"
    description = "Compares every i18n JSON file with $baselineFileName"
    val languageFolder = file("src/main/resources/resources/liquidbounce/lang")
    baselineFile.set(languageFolder.resolve(baselineFileName))
    files.from(languageFolder.listFilesOrdered { it.extension.equals("json", ignoreCase = true) })
    consoleOutputCount.set(5)
}

tasks.register<SourceQualityGateTask>("sourceQualityGate") {
    dependsOn("npmInstallTheme")
}
