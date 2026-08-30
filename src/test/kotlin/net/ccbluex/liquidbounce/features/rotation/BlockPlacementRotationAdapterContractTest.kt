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

package net.ccbluex.liquidbounce.features.rotation

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class BlockPlacementRotationAdapterContractTest {

    @Test
    fun `adapter preserves rotation settings module gate and bootstrap order`() {
        val adapter = source("features/rotation/BlockPlacementRotationAdapter.kt")
        assertTrue(adapter.contains("val rotations = RotationsValueGroup(owner)"))
        assertTrue(adapter.contains("valueGroup = rotations"))
        assertTrue(adapter.contains("targetFactory = rotations"))
        assertTrue(adapter.contains("owner as? ClientModule"))
        assertTrue(adapter.contains("PostRotationExecutor.addTask(module, postMove, priority, task)"))

        val initializer = source("bootstrap/liquidbounce/ClientManagerInitializer.kt")
        val initializeStage = initializer
            .substringAfter(") = withContext(renderThreadDispatcher) {")
            .substringBefore("private fun initializeUtilityListeners()")
        assertOrdered(initializeStage, "initializeUtilityListeners()", "initializeFeatureManagers()")

        val utilityListeners = initializer
            .substringAfter("private fun initializeUtilityListeners() {")
            .substringBefore("private fun initializeFeatureManagers()")
        val featureManagers = initializer
            .substringAfter("private fun initializeFeatureManagers() {")
            .substringBefore("private fun initializeUtilityManagers()")
        assertTrue(utilityListeners.contains("BlockPlacementRotationAdapter.install()"))
        assertTrue(featureManagers.contains("ModuleManager"))
    }

    private fun source(relativePath: String): String = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/$relativePath"
    ).readText()

    private fun assertOrdered(source: String, first: String, second: String) {
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        assertTrue(firstIndex >= 0, "Missing lifecycle token: $first")
        assertTrue(secondIndex > firstIndex, "Expected $first before $second")
    }
}
