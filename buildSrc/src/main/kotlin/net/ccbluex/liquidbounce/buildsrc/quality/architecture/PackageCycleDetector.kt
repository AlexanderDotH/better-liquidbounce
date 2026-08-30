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

class PackageCycleDetector(dependencies: Collection<PackageDependency>) {
    private val adjacency = dependencies
        .groupBy(PackageDependency::sourcePackage, PackageDependency::targetPackage)
        .mapValues { (_, targets) -> targets.distinct().sorted() }
    private val nodes = dependencies.flatMap { listOf(it.sourcePackage, it.targetPackage) }.toSortedSet()
    private val indices = mutableMapOf<String, Int>()
    private val lowLinks = mutableMapOf<String, Int>()
    private val stack = ArrayDeque<String>()
    private val onStack = mutableSetOf<String>()
    private val components = mutableListOf<Set<String>>()
    private var nextIndex = 0

    fun cyclicComponents(): List<Set<String>> {
        nodes.forEach { node -> if (node !in indices) visit(node) }
        return components.filter { it.size > 1 }.sortedBy { it.sorted().joinToString("|") }
    }

    private fun visit(node: String) {
        indices[node] = nextIndex
        lowLinks[node] = nextIndex
        nextIndex++
        stack.addLast(node)
        onStack += node

        adjacency[node].orEmpty().forEach { target -> inspectEdge(node, target) }
        if (lowLinks[node] == indices[node]) components += popComponent(node)
    }

    private fun inspectEdge(node: String, target: String) {
        if (target !in indices) {
            visit(target)
            lowLinks[node] = minOf(checkNotNull(lowLinks[node]), checkNotNull(lowLinks[target]))
        } else if (target in onStack) {
            lowLinks[node] = minOf(checkNotNull(lowLinks[node]), checkNotNull(indices[target]))
        }
    }

    private fun popComponent(root: String): Set<String> = buildSet {
        do {
            val node = stack.removeLast()
            onStack -= node
            add(node)
        } while (node != root)
    }
}
