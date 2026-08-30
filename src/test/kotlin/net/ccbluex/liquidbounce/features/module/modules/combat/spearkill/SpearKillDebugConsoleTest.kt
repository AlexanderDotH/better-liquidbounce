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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillDebugConsoleTest {

    @Test
    fun `disabled console neither evaluates nor emits diagnostics`() {
        var fieldsEvaluated = false
        var fingerprintEvaluated = false
        val messages = mutableListOf<String>()
        val console = SpearKillDebugConsole(
            enabled = { false },
            sink = messages::add,
        )

        console.log("TARGET_SELECTED") {
            fieldsEvaluated = true
            listOf("target" to "EgliJohn")
        }
        console.logChanged(
            channel = "charge",
            event = "CHARGE_STATE",
            fingerprint = {
                fingerprintEvaluated = true
                "WAIT"
            },
        ) {
            fieldsEvaluated = true
            listOf("state" to "WAIT")
        }

        assertFalse(fieldsEvaluated)
        assertFalse(fingerprintEvaluated)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `enabled console emits parseable one-line structured fields`() {
        val messages = mutableListOf<String>()
        val console = SpearKillDebugConsole(
            enabled = { true },
            sink = messages::add,
        )

        console.log("TARGET_SELECTED") {
            listOf(
                "tick" to 42,
                "target_id" to 7,
                "target_name" to "Egli John\nsecond line",
                "ready" to true,
                "optional" to null,
                "quoted" to "say \"hi\" \\ now",
            )
        }

        assertEquals(
            listOf(
                "[SpearKill][TARGET_SELECTED] tick=42 target_id=7 " +
                    "target_name=\"Egli John\\nsecond line\" ready=true optional=null " +
                    "quoted=\"say \\\"hi\\\" \\\\ now\"",
            ),
            messages,
        )
    }

    @Test
    fun `changed diagnostics suppress identical states but retain transitions`() {
        val messages = mutableListOf<String>()
        val console = SpearKillDebugConsole(
            enabled = { true },
            sink = messages::add,
        )

        repeat(2) {
            console.logChanged("charge", "CHARGE_STATE", fingerprint = { "WAIT" }) {
                listOf("state" to "WAIT", "ticks" to 3)
            }
        }
        console.logChanged("charge", "CHARGE_STATE", fingerprint = { "READY" }) {
            listOf("state" to "READY", "ticks" to 8)
        }

        assertEquals(2, messages.size)
        assertTrue(messages.first().contains("state=\"WAIT\""))
        assertTrue(messages.last().contains("state=\"READY\""))
    }

    @Test
    fun `clearing transition history makes the current state observable again`() {
        val messages = mutableListOf<String>()
        val console = SpearKillDebugConsole(
            enabled = { true },
            sink = messages::add,
        )

        console.logChanged("target", "TARGET_STATE", fingerprint = { 12 }) {
            listOf("target_id" to 12)
        }
        console.clearTransitions()
        console.logChanged("target", "TARGET_STATE", fingerprint = { 12 }) {
            listOf("target_id" to 12)
        }

        assertEquals(2, messages.size)
    }
}
