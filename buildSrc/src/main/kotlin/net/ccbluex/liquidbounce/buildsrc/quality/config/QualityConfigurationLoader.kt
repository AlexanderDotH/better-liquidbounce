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

package net.ccbluex.liquidbounce.buildsrc.quality.config

import groovy.json.JsonSlurper
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.FileLimitPolicy
import net.ccbluex.liquidbounce.buildsrc.quality.analysis.StructuralLimitPolicy
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.ArchitectureComponent
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.ArchitectureContract
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.ArchitecturePolicy
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.OwnershipPolicy
import net.ccbluex.liquidbounce.buildsrc.quality.architecture.RestrictedArchitectureEdge
import java.nio.file.Files
import java.nio.file.Path

object QualityConfigurationLoader {
    fun loadHygiene(path: Path): HygienePolicy {
        val root = path.readObject()
        root.requireSchemaVersion()
        val limits = root.objectValue("limits")
        val methods = root.optionalObject("methods")
        val cluster = root.objectValue("prefixCluster")
        return HygienePolicy(
            includedExtensions = root.stringSet("includedExtensions"),
            excludedDirectoryNames = root.stringSet("excludedDirectoryNames"),
            excludedPathPrefixes = root.stringSet("excludedPathPrefixes"),
            testPathPrefixes = root.stringSet("testPathPrefixes"),
            uiPathPrefixes = root.stringSet("uiPathPrefixes"),
            testFileNamePatterns = root.stringList("testFileNamePatterns").map(::Regex),
            fileLimits = FileLimitPolicy(limits.intValue("production"), limits.intValue("ui"), limits.intValue("test")),
            forbiddenSuppressions = root.stringSet("forbiddenSuppressions"),
            packageRoots = root.stringList("packageRoots"),
            categoryRoots = root.stringSet("categoryRoots"),
            strategyDirectories = root.stringSet("strategyDirectories"),
            minimumClusterFiles = cluster.intValue("minimumFiles"),
            minimumPrefixTokens = cluster.intValue("minimumTokens"),
            structuralLimits = methods?.let(::structuralLimits) ?: StructuralLimitPolicy.DEFAULT,
        ).validateContract()
    }

    fun loadArchitecture(path: Path): ArchitecturePolicy {
        val root = path.readObject()
        root.requireSchemaVersion()
        val ownership = root.optionalObject("ownership")
        return ArchitectureContract.validate(ArchitecturePolicy(
            internalPackagePrefix = root.stringValue("internalPackagePrefix"),
            analyzedPathPrefixes = root.stringSet("analyzedPathPrefixes"),
            components = root.objectList("components").map(::component),
            restrictedEdges = root.objectList("restrictedEdges").map(::restrictedEdge),
            ownership = ownership?.let(::ownershipPolicy) ?: OwnershipPolicy.DEFAULT,
        ))
    }

    private fun ownershipPolicy(value: Map<String, Any?>) = OwnershipPolicy(
        featureComponentIds = value.stringSet("featureComponentIds"),
        roleSegments = value.stringSet("roleSegments"),
        collectionSegments = value.stringSet("collectionSegments"),
    )

    private fun structuralLimits(value: Map<String, Any?>) = StructuralLimitPolicy(
        productionMethodLines = value.intValue("productionLines"),
        testMethodLines = value.intValue("testLines"),
        cognitiveComplexity = value.intValue("complexity"),
        nestingDepth = value.intValue("nesting"),
    )

    private fun component(value: Map<String, Any?>) = ArchitectureComponent(
        id = value.stringValue("id"),
        exactPackages = value.stringSet("exactPackages"),
        packagePrefixes = value.stringSet("packagePrefixes"),
        allowedDependencies = value.stringSet("allowedDependencies"),
    )

    private fun restrictedEdge(value: Map<String, Any?>) = RestrictedArchitectureEdge(
        fromComponent = value.stringValue("fromComponent"),
        toComponent = value.stringValue("toComponent"),
        sourcePackagePrefixes = value.stringSet("sourcePackagePrefixes"),
        allowedImportPatterns = value.stringList("allowedImportPatterns").map(::Regex),
    )
}

private fun Path.readObject(): Map<String, Any?> {
    val parsed = JsonSlurper().parseText(Files.readString(this))
    require(parsed is Map<*, *>) { "$this must contain a JSON object" }
    @Suppress("UNCHECKED_CAST")
    return parsed as Map<String, Any?>
}

private fun Map<String, Any?>.requireSchemaVersion() {
    require(intValue("schemaVersion") == 1) { "Unsupported source-quality schema version" }
}

private fun Map<String, Any?>.stringValue(name: String) = requireNotNull(this[name] as? String) { "Missing string '$name'" }
private fun Map<String, Any?>.intValue(name: String) = requireNotNull((this[name] as? Number)?.toInt()) { "Missing integer '$name'" }
private fun Map<String, Any?>.stringList(name: String) = listValue(name).map { value ->
    requireNotNull(value as? String) { "'$name' must contain only strings" }
}
private fun Map<String, Any?>.stringSet(name: String) = stringList(name).toSet()
private fun Map<String, Any?>.objectList(name: String) = listValue(name).map { value ->
    require(value is Map<*, *>) { "'$name' must contain only objects" }
    @Suppress("UNCHECKED_CAST")
    value as Map<String, Any?>
}
private fun Map<String, Any?>.listValue(name: String) = requireNotNull(this[name] as? List<*>) { "Missing array '$name'" }
private fun Map<String, Any?>.objectValue(name: String) = requireNotNull(optionalObject(name)) { "Missing object '$name'" }
private fun Map<String, Any?>.optionalObject(name: String): Map<String, Any?>? {
    val value = this[name] ?: return null
    require(value is Map<*, *>) { "'$name' must be an object" }
    @Suppress("UNCHECKED_CAST")
    return value as Map<String, Any?>
}
