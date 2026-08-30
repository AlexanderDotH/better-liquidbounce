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

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

val sourceSets = extensions.getByType<SourceSetContainer>()

tasks.register<JavaExec>("liquidInstruction") {
    group = "other"
    description = "Runs the LiquidInstruction class"
    classpath = sourceSets.getByName("main").runtimeClasspath
    mainClass.set("net.ccbluex.liquidbounce.LiquidInstruction")
}

tasks.named<Jar>("jar") {
    val archivesBaseName = providers.gradleProperty("archives_base_name")
    val modVersion = providers.gradleProperty("mod_version")
    val mavenGroup = providers.gradleProperty("maven_group")
    inputs.property("archives_base_name", archivesBaseName)
    inputs.property("mod_version", modVersion)
    inputs.property("maven_group", mavenGroup)
    manifest {
        attributes["Main-Class"] = "net.ccbluex.liquidbounce.LiquidInstruction"
        attributes["Implementation-Title"] = archivesBaseName.get()
        attributes["Implementation-Version"] = modVersion.get()
        attributes["Implementation-Vendor"] = mavenGroup.get()
    }
    from("LICENSE") {
        rename { "${it}_${archivesBaseName.get()}" }
    }
}

tasks.register<Copy>("copyZipInclude") {
    from("zip_include/")
    from("third_party/baritone/baritone-1.15.0-10-g2991d921-sources.tar.gz") {
        into("sources")
    }
    into("build/libs/zip")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn("buildTheme", "generateGitProperties")
    from("src-theme/dist") {
        into("resources/liquidbounce/themes/liquidbounce")
    }
}

tasks.named("build") {
    dependsOn("copyZipInclude")
}
