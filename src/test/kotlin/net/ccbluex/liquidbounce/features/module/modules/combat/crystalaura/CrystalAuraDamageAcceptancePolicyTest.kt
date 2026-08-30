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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrystalAuraDamageAcceptancePolicyTest {

    @Test
    fun `rejected enemy damage does not evaluate self or friend damage`() {
        val evaluated = mutableListOf<String>()

        val result = acceptCrystalDamage(
            damageToTarget = NormalDamageProvider(4.9f),
            selfDamage = { evaluated += "self"; NormalDamageProvider(1f) },
            unsafeFriendDamage = { evaluated += "friend"; false },
            playerHealth = { 20f },
            limits = limits(),
        )

        assertNull(result)
        assertEquals(emptyList(), evaluated)
    }

    @Test
    fun `unsafe self damage rejects before evaluating friends`() {
        val evaluated = mutableListOf<String>()

        val result = acceptCrystalDamage(
            damageToTarget = NormalDamageProvider(6f),
            selfDamage = { evaluated += "self"; NormalDamageProvider(3f) },
            unsafeFriendDamage = { evaluated += "friend"; false },
            playerHealth = { 20f },
            limits = limits(),
        )

        assertNull(result)
        assertEquals(listOf("self"), evaluated)
    }

    @Test
    fun `friend and efficiency checks preserve rejection order`() {
        assertNull(acceptCrystalDamage(
            damageToTarget = NormalDamageProvider(6f),
            selfDamage = { NormalDamageProvider(1f) },
            unsafeFriendDamage = { true },
            playerHealth = { 20f },
            limits = limits(),
        ))
        assertNull(acceptCrystalDamage(
            damageToTarget = NormalDamageProvider(6f),
            selfDamage = { NormalDamageProvider(6f) },
            unsafeFriendDamage = { false },
            playerHealth = { 20f },
            limits = limits(),
        ))
    }

    @Test
    fun `accepted damage returns fixed self then target values`() {
        val result = acceptCrystalDamage(
            damageToTarget = NormalDamageProvider(7f),
            selfDamage = { NormalDamageProvider(1.5f) },
            unsafeFriendDamage = { false },
            playerHealth = { 20f },
            limits = limits(),
        )

        assertEquals(1.5f, result?.firstFloat())
        assertEquals(7f, result?.secondFloat())
    }

    private fun limits() = CrystalDamageLimits(
        minEnemyDamage = 5f,
        maxSelfDamage = 2f,
        antiSuicide = true,
        efficient = true,
    )
}
