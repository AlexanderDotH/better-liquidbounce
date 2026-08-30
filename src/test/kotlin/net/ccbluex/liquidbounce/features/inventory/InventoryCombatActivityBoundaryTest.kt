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
package net.ccbluex.liquidbounce.features.inventory

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InventoryCombatActivityBoundaryTest {

    @Test
    fun `inventory constraints read combat activity through the neutral port`() {
        val constraints = read(INVENTORY_CONSTRAINTS)

        assertFalse("net.ccbluex.liquidbounce.features.combat.runtime" in constraints)
        assertTrue("import net.ccbluex.liquidbounce.common.combat.CombatActivity" in constraints)
        assertTrue("NOT_DURING_COMBAT -> !CombatActivity.isInCombat" in constraints)
    }

    @Test
    fun `combat adapter delegates each activity query to CombatManager`() {
        val adapter = read(COMBAT_ADAPTER)

        assertTrue("object CombatActivityAdapter : CombatActivityPort" in adapter)
        assertTrue("override fun isInCombat() = CombatManager.isInCombat" in adapter)
    }

    @Test
    fun `combat activity binding follows its manager and precedes inventory initialization`() {
        val utilityManagers = read(CLIENT_MANAGER_INITIALIZER)
            .substringAfter("private fun initializeUtilityManagers()")
        val manager = utilityManagers.indexOf("CombatManager")
        val binding = utilityManagers.indexOf("CombatActivityAdapter.install()")
        val inventory = utilityManagers.indexOf("InventoryManager")

        assertTrue(manager >= 0)
        assertTrue(binding >= 0)
        assertTrue(inventory >= 0)
        assertTrue(manager < binding)
        assertTrue(binding < inventory)
    }

    private fun read(path: Path): String = Files.readString(path)

    private companion object {
        val INVENTORY_CONSTRAINTS: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/inventory/InventoryConstraints.kt",
        )
        val COMBAT_ADAPTER: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/combat/runtime/CombatActivityAdapter.kt",
        )
        val CLIENT_MANAGER_INITIALIZER: Path = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/bootstrap/liquidbounce/ClientManagerInitializer.kt",
        )
    }
}
