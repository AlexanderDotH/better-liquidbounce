/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * Merges the fixed SeedFinding stack into one Jar-in-Jar dependency and moves its packages below LiquidBounce.
 *
 * ViaFabricPlus bundles an unrelated, older copy of `com.seedfinding`. Fabric's nested-jar loader makes that
 * copy visible before arbitrary sibling JIJ dependencies, so simply pinning Gradle's resolution is not enough.
 * Relocation gives the SeedCracker its own immutable implementation without modifying ViaFabricPlus.
 */
abstract class RelocateSeedFindingJarsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceJars: ConfigurableFileCollection

    @get:Input
    abstract val sourceCoordinates: ListProperty<String>

    @get:Input
    abstract val sourcePackage: Property<String>

    @get:Input
    abstract val targetPackage: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    init {
        sourcePackage.convention("com.seedfinding")
        targetPackage.convention("net.ccbluex.liquidbounce.seedcracker.seedfinding")
    }

    @TaskAction
    fun relocate() {
        val sourceInternalName = sourcePackage.get().replace('.', '/')
        val targetInternalName = targetPackage.get().replace('.', '/')
        val remapper = SeedFindingRemapper(sourceInternalName, targetInternalName)
        val output = outputJar.get().asFile.toPath()

        Files.createDirectories(checkNotNull(output.parent))
        val writtenEntries = mutableSetOf<String>()

        JarOutputStream(Files.newOutputStream(output)).use { destination ->
            sourceJars.files.sortedBy { it.name }.forEach { source ->
                JarFile(source).use { input ->
                    val entries = input.entries()

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory || shouldSkip(entry.name)) {
                            continue
                        }

                        val targetEntry = relocateEntry(entry.name, input.getInputStream(entry).readBytes(), remapper)
                        if (!writtenEntries.add(targetEntry.name)) {
                            throw GradleException(
                                "Duplicate SeedFinding entry '${targetEntry.name}' while relocating ${source.name}",
                            )
                        }

                        destination.putNextEntry(ZipEntry(targetEntry.name).apply { time = 0L })
                        destination.write(targetEntry.bytes)
                        destination.closeEntry()
                    }
                }
            }

            val lockEntry = "META-INF/liquidbounce-seedcracker-seedfinding.lock"
            destination.putNextEntry(ZipEntry(lockEntry).apply { time = 0L })
            destination.write(sourceCoordinates.get().sorted().joinToString(separator = "\n", postfix = "\n").toByteArray())
            destination.closeEntry()
        }
    }

    private fun shouldSkip(entryName: String) = entryName == "module-info.class" ||
        entryName.startsWith("META-INF/") && !entryName.startsWith("META-INF/services/")

    private fun relocateEntry(
        entryName: String,
        bytes: ByteArray,
        remapper: SeedFindingRemapper,
    ): RelocatedEntry = when {
        entryName.endsWith(".class") -> {
            val reader = ClassReader(bytes)
            val writer = ClassWriter(0)
            reader.accept(ClassRemapper(writer, remapper), 0)
            RelocatedEntry("${remapper.map(reader.className)}.class", writer.toByteArray())
        }

        entryName.startsWith("META-INF/services/") -> {
            val service = entryName.removePrefix("META-INF/services/")
            val relocatedService = remapper.map(service.replace('.', '/')).replace('/', '.')
            val relocatedContents = bytes.toString(StandardCharsets.UTF_8)
                .lineSequence()
                .joinToString(separator = "\n") { line -> relocateServiceLine(line, remapper) }
                .toByteArray(StandardCharsets.UTF_8)

            RelocatedEntry("META-INF/services/$relocatedService", relocatedContents)
        }

        else -> RelocatedEntry(relocateResourcePath(entryName, remapper), bytes)
    }

    private fun relocateResourcePath(entryName: String, remapper: SeedFindingRemapper): String {
        val sourcePrefix = "${sourcePackage.get().replace('.', '/')}/"
        return if (entryName.startsWith(sourcePrefix)) {
            "${remapper.map(sourcePrefix.removeSuffix("/"))}/${entryName.removePrefix(sourcePrefix)}"
        } else {
            entryName
        }
    }

    private fun relocateServiceLine(line: String, remapper: SeedFindingRemapper): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('#')) {
            return line
        }

        val indentation = line.takeWhile(Char::isWhitespace)
        return "$indentation${remapper.map(trimmed.replace('.', '/')).replace('/', '.')}"
    }

    private data class RelocatedEntry(val name: String, val bytes: ByteArray)

    private class SeedFindingRemapper(
        private val sourceInternalName: String,
        private val targetInternalName: String,
    ) : Remapper() {
        override fun map(internalName: String): String = when {
            internalName == sourceInternalName -> targetInternalName
            internalName.startsWith("$sourceInternalName/") ->
                "$targetInternalName${internalName.removePrefix(sourceInternalName)}"

            else -> internalName
        }
    }
}
