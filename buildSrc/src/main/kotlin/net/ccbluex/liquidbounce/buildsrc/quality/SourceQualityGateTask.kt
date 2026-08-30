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

import net.ccbluex.liquidbounce.buildsrc.quality.report.QualityReportRenderer
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The gate compares live Git state with the checked-in ratchet")
abstract class SourceQualityGateTask : DefaultTask() {

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val hygieneConfiguration: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val architectureConfiguration: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ratchetConfiguration: RegularFileProperty

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    @get:Optional
    @get:Input
    abstract val baseRevision: Property<String>

    @get:Input
    abstract val additionalTouchedPaths: ListProperty<String>

    @get:Input
    abstract val ratchetMode: Property<String>

    init {
        group = "verification"
        description = "Enforces source hygiene, package architecture, and the monotone debt ratchet."
        repositoryRoot.convention(project.layout.projectDirectory)
        hygieneConfiguration.convention(project.layout.projectDirectory.file("config/source-hygiene.json"))
        architectureConfiguration.convention(project.layout.projectDirectory.file("config/source-architecture.json"))
        ratchetConfiguration.convention(project.layout.projectDirectory.file("config/source-ratchet.json"))
        reportDirectory.convention(project.layout.buildDirectory.dir("reports/source-hygiene"))
        baseRevision.convention(project.providers.gradleProperty("sourceQualityBaseRevision"))
        additionalTouchedPaths.convention(
            project.providers.gradleProperty("sourceQualityTouchedPaths")
                .map { value -> value.split(',').map(String::trim).filter(String::isNotEmpty) }
                .orElse(emptyList()),
        )
        ratchetMode.convention(project.providers.gradleProperty("sourceQualityRatchetMode").orElse("compare"))
    }

    @TaskAction
    fun verifySourceQuality() {
        val root = repositoryRoot.get().asFile.toPath()
        val ratchet = ratchetConfiguration.get().asFile.toPath()
        val gitState = GitQualityState.load(root, ratchet, baseRevision.orNull)
        val touchedPaths = (gitState.touchedPaths + additionalTouchedPaths.get())
            .mapTo(sortedSetOf()) { it.replace('\\', '/') }
        val result = SourceQualityGate.run(
            SourceQualityRequest(
                repositoryRoot = root,
                hygieneConfiguration = hygieneConfiguration.get().asFile.toPath(),
                architectureConfiguration = architectureConfiguration.get().asFile.toPath(),
                ratchetConfiguration = ratchet,
                reportDirectory = reportDirectory.get().asFile.toPath(),
                touchedPaths = touchedPaths,
                referenceBaseline = gitState.referenceBaseline,
                ratchetMode = RatchetMode.parse(ratchetMode.get()),
                capturedRevision = gitState.currentRevision,
            ),
        )
        logger.lifecycle(QualityReportRenderer.render(result).console)
        if (result.hasBlockingFindings) {
            throw GradleException(
                "Source quality gate found ${result.blockingCount} blocking finding(s). " +
                    "Read ${reportDirectory.get().asFile.resolve("source-quality.md")}.",
            )
        }
    }
}
