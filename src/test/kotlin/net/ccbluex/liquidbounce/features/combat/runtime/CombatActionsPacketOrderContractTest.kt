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
package net.ccbluex.liquidbounce.features.combat.runtime

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class CombatActionsPacketOrderContractTest {

    @Test
    fun `accepted attack keeps rejection hook packet feedback cooldown and swing order`() {
        val source = Files.readString(Path.of(SOURCE))
        val entryPoint = declaration(source, "fun attackEntityWithResult(")
        val commit = declaration(source, "private fun commitAcceptedAttack(")

        assertInOrder(entryPoint, "performPiercingAttack", "isRejectedAttack", "commitAcceptedAttack")
        assertInOrder(
            commit,
            "if (isOlderThanOrEqual1_8)",
            "interaction.ensureHasSentCarriedItem()",
            "AcceptedAttackHook.commit(this, entity)",
            "if (!acceptedAttackResult.allowsAttack)",
            "network.send(ServerboundAttackPacket(entity.id))",
            "if (keepSprint)",
            "attackStrengthTicker = 0",
            "if (!isOlderThanOrEqual1_8)",
        )
    }

    @Test
    fun `piercing attack remains isolated from the ordinary attack packet path`() {
        val source = Files.readString(Path.of(SOURCE))
        val piercing = declaration(source, "private fun performPiercingAttack(")

        assertInOrder(
            piercing,
            "interaction.piercingAttack(piercingWeapon)",
            "swing.swing(InteractionHand.MAIN_HAND)",
            "AcceptedAttackResult.NOT_APPLIED",
        )
        assertTrue("ServerboundAttackPacket" !in piercing)
    }

    private fun assertInOrder(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker, previous + 1)
            assertTrue(index > previous, "$marker is missing or out of order")
            previous = index
        }
    }

    private fun declaration(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "Missing declaration: $marker" }
        val openingBrace = source.indexOf('{', markerIndex)
        require(openingBrace >= 0) { "Missing declaration body: $marker" }
        var depth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(markerIndex, index + 1)
            }
        }
        error("Unclosed declaration: $marker")
    }

    private companion object {
        const val SOURCE =
            "src/main/kotlin/net/ccbluex/liquidbounce/features/combat/runtime/CombatActions.kt"
    }
}
