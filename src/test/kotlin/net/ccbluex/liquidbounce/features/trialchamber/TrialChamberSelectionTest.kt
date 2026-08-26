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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrialChamberSelectionTest {

    @Test
    fun `cluster boundaries include 96 horizontal and 64 vertical blocks`() {
        assertEquals(
            1,
            TrialChamberClusterer.cluster(
                listOf(
                    anchor(0, 0, 0, TrialChamberAnchorKind.VAULT),
                    anchor(96, 64, 0, TrialChamberAnchorKind.VAULT),
                ),
            ).size,
        )
        assertEquals(
            2,
            TrialChamberClusterer.cluster(
                listOf(anchor(0, 0, 0), anchor(97, 64, 0)),
            ).size,
        )
        assertEquals(
            2,
            TrialChamberClusterer.cluster(
                listOf(anchor(0, 0, 0), anchor(96, 65, 0)),
            ).size,
        )
        assertEquals(
            2,
            TrialChamberClusterer.cluster(
                listOf(anchor(0, 0, 0), anchor(96, 0, 1)),
            ).size,
        )
    }

    @Test
    fun `cluster connectivity is transitive`() {
        val clusters = TrialChamberClusterer.cluster(
            listOf(
                anchor(192, 0, 0),
                anchor(0, 0, 0),
                anchor(96, 0, 0),
            ),
        )

        assertEquals(1, clusters.size)
        assertEquals(
            listOf(0, 96, 192),
            clusters.single().anchors.map { it.position.x },
        )
    }

    @Test
    fun `active spawner cluster wins before a nearer inactive cluster`() {
        val inactive = anchor(8, 0, 0, TrialChamberAnchorKind.VAULT)
        val active = anchor(120, 0, 0, TrialChamberAnchorKind.TRIAL_SPAWNER, active = true)

        val selected = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(0.0, 0.0, 0.0),
            loadedAnchors = listOf(inactive, active),
        )

        assertEquals(active.position, selected?.cluster?.anchors?.single()?.position)
    }

    @Test
    fun `hysteresis retains the current cluster until the challenger is more than 16 blocks closer`() {
        val left = anchor(0, 0, 0, TrialChamberAnchorKind.VAULT)
        val right = anchor(128, 0, 0, TrialChamberAnchorKind.VAULT)
        val initial = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(56.0, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
        )

        val withinBand = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(72.0, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
            previous = initial,
        )
        val outsideBand = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(72.1, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
            previous = withinBand,
        )

        assertEquals(left.position, withinBand?.cluster?.anchors?.single()?.position)
        assertEquals(right.position, outsideBand?.cluster?.anchors?.single()?.position)
    }

    @Test
    fun `activity priority overrides hysteresis in both directions`() {
        val left = anchor(0, 0, 0, TrialChamberAnchorKind.TRIAL_SPAWNER, active = true)
        val right = anchor(128, 0, 0, TrialChamberAnchorKind.VAULT)
        val activeSelection = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(0.0, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
        )

        val retained = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(120.0, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
            previous = activeSelection,
        )
        val rightBecomesActive = right.copy(
            kind = TrialChamberAnchorKind.TRIAL_SPAWNER,
            activeSpawner = true,
        )
        val switched = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(120.0, 0.0, 0.0),
            loadedAnchors = listOf(left.copy(activeSpawner = false), rightBecomesActive),
            previous = retained,
        )

        assertEquals(left.position, retained?.cluster?.anchors?.single()?.position)
        assertEquals(right.position, switched?.cluster?.anchors?.single()?.position)
    }

    @Test
    fun `an unloaded current cluster is replaced without hysteresis`() {
        val current = anchor(0, 0, 0, TrialChamberAnchorKind.VAULT)
        val replacement = anchor(128, 0, 0, TrialChamberAnchorKind.VAULT)
        val initial = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(0.0, 0.0, 0.0),
            loadedAnchors = listOf(current, replacement),
        )

        val selected = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(1.0, 0.0, 0.0),
            loadedAnchors = listOf(replacement),
            previous = initial,
        )

        assertEquals(replacement.position, selected?.cluster?.anchors?.single()?.position)
    }

    @Test
    fun `selection beyond 192 blocks releases or chooses an in-range replacement`() {
        val current = anchor(0, 0, 0, TrialChamberAnchorKind.VAULT)
        val replacement = anchor(350, 0, 0, TrialChamberAnchorKind.VAULT)
        val initial = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(0.0, 0.0, 0.0),
            loadedAnchors = listOf(current, replacement),
        )

        val replaced = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(200.0, 0.0, 0.0),
            loadedAnchors = listOf(current, replacement),
            previous = initial,
        )
        val released = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(600.0, 0.0, 0.0),
            loadedAnchors = listOf(current, replacement),
            previous = replaced,
        )

        assertEquals(replacement.position, replaced?.cluster?.anchors?.single()?.position)
        assertNull(released)
    }

    @Test
    fun `selection includes exactly 192 blocks and excludes any greater distance`() {
        val anchor = anchor(0, 0, 0, TrialChamberAnchorKind.VAULT)

        val atBoundary = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(192.0, 0.0, 0.0),
            loadedAnchors = listOf(anchor),
        )
        val outsideBoundary = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(192.1, 0.0, 0.0),
            loadedAnchors = listOf(anchor),
            previous = atBoundary,
        )

        assertEquals(anchor.position, atBoundary?.cluster?.anchors?.single()?.position)
        assertNull(outsideBoundary)
    }

    @Test
    fun `world epoch change discards hysteresis state`() {
        val left = anchor(0, 0, 0, TrialChamberAnchorKind.VAULT)
        val right = anchor(128, 0, 0, TrialChamberAnchorKind.VAULT)
        val previousWorld = TrialChamberSelector.select(
            worldEpoch = 1L,
            observer = position(56.0, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
        )

        val selected = TrialChamberSelector.select(
            worldEpoch = 2L,
            observer = position(65.0, 0.0, 0.0),
            loadedAnchors = listOf(left, right),
            previous = previousWorld,
        )

        assertEquals(2L, selected?.worldEpoch)
        assertEquals(right.position, selected?.cluster?.anchors?.single()?.position)
    }

    @Test
    fun `container containment uses the nearest anchor and includes exactly 64 blocks`() {
        val cluster = TrialChamberClusterer.cluster(
            listOf(anchor(0, 0, 0), anchor(32, 0, 0)),
        ).single()

        assertEquals(32.0, cluster.nearestAnchorDistanceTo(position(64.0, 0.0, 0.0)))
        assertTrue(cluster.containsContainer(position(96.0, 0.0, 0.0), cluster.anchors))
        assertFalse(cluster.containsContainer(position(96.0, 0.0, 1.0), cluster.anchors))
    }

    @Test
    fun `container belongs only when its globally nearest loaded anchor is in the selected cluster`() {
        val selectedAnchor = anchor(0, 0, 0, TrialChamberAnchorKind.VAULT)
        val competingAnchor = anchor(100, 0, 0, TrialChamberAnchorKind.VAULT)
        val selectedCluster = TrialChamberClusterer.cluster(listOf(selectedAnchor, competingAnchor)).first()

        assertTrue(
            selectedCluster.containsContainer(
                position = position(40.0, 0.0, 0.0),
                loadedAnchors = listOf(selectedAnchor, competingAnchor),
            ),
        )
        assertFalse(
            selectedCluster.containsContainer(
                position = position(60.0, 0.0, 0.0),
                loadedAnchors = listOf(selectedAnchor, competingAnchor),
            ),
        )
    }

    private fun anchor(
        x: Int,
        y: Int,
        z: Int,
        kind: TrialChamberAnchorKind = TrialChamberAnchorKind.TRIAL_SPAWNER,
        active: Boolean = false,
    ) = TrialChamberAnchor(
        position = TrialBlockPosition(x, y, z),
        kind = kind,
        activeSpawner = active,
    )

    private fun position(x: Double, y: Double, z: Double) = TrialWorldPosition(x, y, z)
}
