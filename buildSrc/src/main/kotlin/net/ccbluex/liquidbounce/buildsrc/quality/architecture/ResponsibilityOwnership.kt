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

package net.ccbluex.liquidbounce.buildsrc.quality.architecture

internal class ResponsibilityOwnership(private val policy: ArchitecturePolicy) {
    fun nodeFor(packageName: String): String? {
        val component = policy.componentFor(packageName) ?: return null
        val root = component.matchingRoot(packageName) ?: return component.id
        return if (component.id in policy.ownership.featureComponentIds) {
            featureNode(component.id, root, packageName)
        } else {
            topLevelNode(component.id, root)
        }
    }

    private fun topLevelNode(componentId: String, root: String): String {
        val suffix = root.removePrefix(policy.internalPackagePrefix).trim('.')
        return if (suffix.isEmpty()) componentId else "$componentId:${suffix.substringBefore('.')}"
    }

    private fun featureNode(componentId: String, root: String, packageName: String): String {
        val rawSegments = packageName.removePrefix(root).trim('.').split('.').filter(String::isNotEmpty)
        val segments = rawSegments.dropModuleScaffolding()
        if (segments.isEmpty()) return componentId
        val boundaryIndex = segments.indexOfFirst(::isRole).takeIf { it >= 0 }
        val slice = segments.take(boundaryIndex ?: segments.size).take(featureSliceDepth(rawSegments))
        val owner = "$componentId:${slice.ifEmpty { segments.take(1) }.joinToString("/")}"
        return boundaryIndex?.let { "$owner:${segments[it]}" } ?: owner
    }

    private fun List<String>.dropModuleScaffolding(): List<String> =
        if (take(2) == listOf("module", "modules")) drop(2) else this

    private fun featureSliceDepth(rawSegments: List<String>) =
        if (rawSegments.take(2) == listOf("module", "modules")) 2 else 1

    private fun isRole(segment: String) =
        segment in policy.ownership.roleSegments || segment in policy.ownership.collectionSegments
}
