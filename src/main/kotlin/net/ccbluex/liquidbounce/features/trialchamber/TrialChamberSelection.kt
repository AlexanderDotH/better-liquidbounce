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
package net.ccbluex.liquidbounce.features.trialchamber

import java.util.ArrayDeque
import kotlin.math.sqrt

/** Immutable block coordinate kept independent from scanner-owned Minecraft objects. */
internal data class TrialBlockPosition(val x: Int, val y: Int, val z: Int) {

    internal fun distanceSquaredTo(position: TrialWorldPosition): Double {
        val dx = x - position.x
        val dy = y - position.y
        val dz = z - position.z
        return dx * dx + dy * dy + dz * dz
    }
}

/** Point used by the selection policy for the observer and resource positions. */
internal data class TrialWorldPosition(val x: Double, val y: Double, val z: Double) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Trial Chamber coordinates must be finite" }
    }
}

/** Ominous and normal Vaults deliberately share the same clustering topology. */
internal enum class TrialChamberAnchorKind {
    TRIAL_SPAWNER,
    VAULT,
}

/** A currently loaded Trial Chamber anchor. */
internal data class TrialChamberAnchor(
    val position: TrialBlockPosition,
    val kind: TrialChamberAnchorKind,
    val activeSpawner: Boolean = false,
) {
    init {
        require(!activeSpawner || kind == TrialChamberAnchorKind.TRIAL_SPAWNER) {
            "Only a Trial Spawner anchor can be active"
        }
    }
}

/** One immutable connected component of loaded Trial Spawner and Vault anchors. */
internal class TrialChamberCluster private constructor(anchors: Collection<TrialChamberAnchor>) {

    val anchors: List<TrialChamberAnchor> = java.util.List.copyOf(anchors.sortedWith(ANCHOR_ORDER))

    val hasActiveSpawner: Boolean
        get() = anchors.any(TrialChamberAnchor::activeSpawner)

    init {
        require(this.anchors.isNotEmpty()) { "A Trial Chamber cluster needs at least one anchor" }
    }

    fun nearestAnchorDistanceTo(position: TrialWorldPosition): Double =
        sqrt(anchors.minOf { it.position.distanceSquaredTo(position) })

    fun containsContainer(
        position: TrialWorldPosition,
        loadedAnchors: Collection<TrialChamberAnchor>,
        maximumDistance: Double = TrialChamberSelectionPolicy.CONTAINER_DISTANCE,
    ): Boolean {
        require(maximumDistance.isFinite() && maximumDistance >= 0.0) {
            "Container distance must be finite and non-negative"
        }
        val clusterPositions = anchors.mapTo(HashSet(), TrialChamberAnchor::position)
        val selectedDistance = loadedAnchors.asSequence()
            .filter { it.position in clusterPositions }
            .minOfOrNull { it.position.distanceSquaredTo(position) }
            ?: return false
        val nearestDistance = loadedAnchors.minOfOrNull { it.position.distanceSquaredTo(position) }
            ?: return false
        return selectedDistance <= maximumDistance * maximumDistance && selectedDistance <= nearestDistance
    }

    internal fun sharesAnchorPositionWith(other: TrialChamberCluster): Boolean {
        val otherPositions = other.anchors.mapTo(HashSet(), TrialChamberAnchor::position)
        return anchors.any { it.position in otherPositions }
    }

    override fun equals(other: Any?): Boolean = other is TrialChamberCluster && anchors == other.anchors

    override fun hashCode(): Int = anchors.hashCode()

    override fun toString(): String = "TrialChamberCluster(anchors=$anchors)"

    companion object {
        internal fun from(anchors: Collection<TrialChamberAnchor>) = TrialChamberCluster(anchors)

        internal val ANCHOR_ORDER = compareBy<TrialChamberAnchor>(
            { it.position.x },
            { it.position.y },
            { it.position.z },
        ).thenByDescending(TrialChamberAnchor::activeSpawner)
            .thenBy(TrialChamberAnchor::kind)
    }
}

/** Builds deterministic connected components from the currently loaded anchors. */
internal object TrialChamberClusterer {

    fun cluster(anchors: Collection<TrialChamberAnchor>): List<TrialChamberCluster> {
        val remaining = canonicalAnchors(anchors).toMutableList()
        val clusters = ArrayList<TrialChamberCluster>()
        while (remaining.isNotEmpty()) {
            clusters += collectCluster(remaining.removeFirst(), remaining)
        }
        return java.util.List.copyOf(clusters)
    }

    private fun canonicalAnchors(anchors: Collection<TrialChamberAnchor>): List<TrialChamberAnchor> =
        anchors.sortedWith(TrialChamberCluster.ANCHOR_ORDER)
            .distinctBy(TrialChamberAnchor::position)

    private fun collectCluster(
        start: TrialChamberAnchor,
        remaining: MutableList<TrialChamberAnchor>,
    ): TrialChamberCluster {
        val queue = ArrayDeque<TrialChamberAnchor>()
        val connected = ArrayList<TrialChamberAnchor>()
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val anchor = queue.removeFirst()
            connected += anchor
            moveConnectedAnchors(anchor, remaining, queue)
        }
        return TrialChamberCluster.from(connected)
    }

    private fun moveConnectedAnchors(
        anchor: TrialChamberAnchor,
        remaining: MutableList<TrialChamberAnchor>,
        queue: ArrayDeque<TrialChamberAnchor>,
    ) {
        val iterator = remaining.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (!areConnected(anchor.position, candidate.position)) continue
            iterator.remove()
            queue.addLast(candidate)
        }
    }

    internal fun areConnected(left: TrialBlockPosition, right: TrialBlockPosition): Boolean {
        val verticalDistance = kotlin.math.abs(left.y.toLong() - right.y.toLong())
        if (verticalDistance > TrialChamberSelectionPolicy.CLUSTER_VERTICAL_DISTANCE) return false

        val dx = kotlin.math.abs(left.x.toLong() - right.x.toLong())
        val dz = kotlin.math.abs(left.z.toLong() - right.z.toLong())
        if (dx > TrialChamberSelectionPolicy.CLUSTER_HORIZONTAL_DISTANCE) return false
        if (dz > TrialChamberSelectionPolicy.CLUSTER_HORIZONTAL_DISTANCE) return false
        return dx * dx + dz * dz <= TrialChamberSelectionPolicy.CLUSTER_HORIZONTAL_DISTANCE_SQUARED
    }
}

/** Previous selection state carried across ticks to apply hysteresis without retaining world objects. */
internal data class TrialChamberSelection(
    val worldEpoch: Long,
    val cluster: TrialChamberCluster,
) {
    init {
        require(worldEpoch >= 0L) { "World epoch must be non-negative" }
    }
}

/** Chooses one loaded chamber while preserving a stable current selection inside the hysteresis band. */
internal object TrialChamberSelector {

    fun select(
        worldEpoch: Long,
        observer: TrialWorldPosition,
        loadedAnchors: Collection<TrialChamberAnchor>,
        previous: TrialChamberSelection? = null,
    ): TrialChamberSelection? {
        require(worldEpoch >= 0L) { "World epoch must be non-negative" }
        val eligible = eligibleClusters(observer, loadedAnchors)
        if (eligible.isEmpty()) return null
        if (previous == null || previous.worldEpoch != worldEpoch) {
            return selection(worldEpoch, preferredCluster(eligible, observer))
        }

        val current = currentCluster(previous.cluster, eligible, observer)
            ?: return selection(worldEpoch, preferredCluster(eligible, observer))
        val challenger = preferredCluster(eligible, observer)
        val retained = retainCurrent(current, challenger, observer)
        return selection(worldEpoch, retained)
    }

    private fun eligibleClusters(
        observer: TrialWorldPosition,
        loadedAnchors: Collection<TrialChamberAnchor>,
    ): List<TrialChamberCluster> = TrialChamberClusterer.cluster(loadedAnchors)
        .filter { it.nearestAnchorDistanceTo(observer) <= TrialChamberSelectionPolicy.RELEASE_DISTANCE }

    private fun currentCluster(
        previous: TrialChamberCluster,
        eligible: List<TrialChamberCluster>,
        observer: TrialWorldPosition,
    ): TrialChamberCluster? = eligible.asSequence()
        .filter { it.sharesAnchorPositionWith(previous) }
        .minWithOrNull(clusterDistanceOrder(observer))

    private fun preferredCluster(
        eligible: List<TrialChamberCluster>,
        observer: TrialWorldPosition,
    ): TrialChamberCluster {
        val active = eligible.filter(TrialChamberCluster::hasActiveSpawner)
        val preferred = active.ifEmpty { eligible }
        return preferred.minWith(clusterDistanceOrder(observer))
    }

    private fun retainCurrent(
        current: TrialChamberCluster,
        challenger: TrialChamberCluster,
        observer: TrialWorldPosition,
    ): TrialChamberCluster {
        if (current == challenger) return current
        if (current.hasActiveSpawner != challenger.hasActiveSpawner) {
            return if (challenger.hasActiveSpawner) challenger else current
        }

        val currentDistance = current.nearestAnchorDistanceTo(observer)
        val challengerDistance = challenger.nearestAnchorDistanceTo(observer)
        return if (challengerDistance + TrialChamberSelectionPolicy.HYSTERESIS_DISTANCE < currentDistance) {
            challenger
        } else {
            current
        }
    }

    private fun clusterDistanceOrder(observer: TrialWorldPosition) =
        compareBy<TrialChamberCluster> { it.nearestAnchorDistanceTo(observer) }
            .thenBy { it.anchors.first().position.x }
            .thenBy { it.anchors.first().position.y }
            .thenBy { it.anchors.first().position.z }

    private fun selection(worldEpoch: Long, cluster: TrialChamberCluster) =
        TrialChamberSelection(worldEpoch, cluster)
}

internal object TrialChamberSelectionPolicy {
    const val CLUSTER_HORIZONTAL_DISTANCE = 96L
    const val CLUSTER_VERTICAL_DISTANCE = 64L
    const val HYSTERESIS_DISTANCE = 16.0
    const val RELEASE_DISTANCE = 192.0
    const val CONTAINER_DISTANCE = 64.0

    const val CLUSTER_HORIZONTAL_DISTANCE_SQUARED =
        CLUSTER_HORIZONTAL_DISTANCE * CLUSTER_HORIZONTAL_DISTANCE
}
