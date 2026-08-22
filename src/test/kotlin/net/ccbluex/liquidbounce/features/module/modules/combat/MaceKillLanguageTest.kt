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
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class MaceKillLanguageTest {

    @Test
    fun `MaceKill and MaceClip expose matching nonblank English and German contracts`() {
        ModuleMaceKill.walkKeyPath()
        val english = readLocale("en_us")
        val german = readLocale("de_de")
        val generatedSettingKeys = buildSet {
            ModuleMaceKill.collectValuesRecursively().mapNotNullTo(this) { it.descriptionKey }
            ModuleMaceKill.collectValueGroupsRecursively().mapNotNullTo(this) { it.descriptionKey }
        }.filter { it.startsWith(MACE_KILL_PREFIX) }.toSet()
        val requiredKeys = REQUIRED_KEYS + generatedSettingKeys + clipReachContractKeys()

        assertRequiredContracts(english, german, requiredKeys)
        assertRetiredContractsAbsent(english, german)
        assertMatchingLocalizedContracts(english, german)
        assertIntegrationDescriptions(english, german)
    }

    private fun assertRequiredContracts(english: JsonObject, german: JsonObject, requiredKeys: Set<String>) {
        assertTrue(english.keySet().containsAll(requiredKeys), "en_us missing ${requiredKeys - english.keySet()}")
        assertTrue(german.keySet().containsAll(requiredKeys), "de_de missing ${requiredKeys - german.keySet()}")
    }

    private fun assertRetiredContractsAbsent(english: JsonObject, german: JsonObject) {
        listOf(english, german).forEach { locale ->
            assertTrue(
                RETIRED_KEYS.none(locale::has),
                "retired MaceKill keys remain: ${RETIRED_KEYS.filter(locale::has)}",
            )
            assertFalse(
                locale.keySet().any { key -> key.startsWith("$MACE_KILL_PREFIX.sneakWhileMoving") },
                "MaceKill must not expose SneakWhileMoving localization",
            )
            assertFalse(
                locale.keySet().any { key -> key.startsWith("$MACE_KILL_PREFIX.elytraWhileMoving") },
                "MaceKill must not expose ElytraWhileMoving localization",
            )
        }
    }

    private fun assertMatchingLocalizedContracts(english: JsonObject, german: JsonObject) {
        PREFIXES.forEach { prefix ->
            assertEquals(keysWithPrefix(english, prefix), keysWithPrefix(german, prefix), prefix)
        }
        val localizedContractKeys = PREFIXES.flatMapTo(sortedSetOf()) { prefix ->
            keysWithPrefix(english, prefix)
        }
        localizedContractKeys.forEach { key ->
            assertTrue(english[key].asString.isNotBlank(), "en_us: $key")
            assertTrue(german[key].asString.isNotBlank(), "de_de: $key")
            assertEquals(
                PLACEHOLDER_REGEX.findAll(english[key].asString).map { it.value }.toList(),
                PLACEHOLDER_REGEX.findAll(german[key].asString).map { it.value }.toList(),
                "placeholder schema: $key",
            )
        }
    }

    private fun assertIntegrationDescriptions(english: JsonObject, german: JsonObject) {
        listOf(english, german).forEach { locale ->
            val fightBot = locale["liquidbounce.module.fightBot.extendedDescription"].asString
            listOf("MaceKill", "MaceAutomation", "Off").forEach { term ->
                assertTrue(fightBot.contains(term), "FightBot: $term")
            }
            val delegation = locale["liquidbounce.settings.combat.delegateKillAuraAttacks.description"].asString
            listOf("MaceKill", "SpearKill", "SuperHit").forEach { term ->
                assertTrue(delegation.contains(term), "DelegateKillAuraAttacks: $term")
            }
            val routing = locale["$MACE_KILL_PREFIX.movement.packet.routing.description"].asString
            listOf("Direct", "AStar", "Instant").forEach { term ->
                assertTrue(routing.contains(term), "MaceKill routing: $term")
            }
            assertFalse(routing.contains("NetworkOptimized"), "MaceKill routing still advertises NetworkOptimized")
            val experimentalWarning = locale["$INSTANT_PREFIX.experimentalWarning"].asString
            assertTrue(experimentalWarning.contains("UNVALIDATED"), "Instant must disclose validation state")
            assertTrue(experimentalWarning.contains("server", ignoreCase = true), "Instant must disclose server risk")
            val groundSpoofWarning = locale["$INSTANT_PREFIX.groundSpoof.warning"].asString
            listOf("Direct", "AStar").forEach { excludedMode ->
                assertTrue(groundSpoofWarning.contains(excludedMode), "Ground spoof isolation: $excludedMode")
            }
            assertTrue(
                groundSpoofWarning.contains("server", ignoreCase = true),
                "Ground spoof must disclose server risk",
            )
        }
    }

    @Test
    fun `English and German locale files contain unique top-level keys`() {
        listOf("en_us", "de_de").forEach { locale ->
            val keys = TOP_LEVEL_KEY_REGEX.findAll(readLocaleSource(locale)).map { it.groupValues[1] }.toList()
            val duplicates = keys.groupingBy { it }.eachCount().filterValues { count -> count > 1 }.keys

            assertTrue(duplicates.isEmpty(), "$locale duplicate keys: $duplicates")
        }
    }

    private fun keysWithPrefix(locale: JsonObject, prefix: String) =
        locale.keySet().filterTo(sortedSetOf()) { it.startsWith(prefix) }

    private fun clipReachContractKeys() = buildSet {
        MaceClipReachProfileValidation.entries.mapTo(this) {
            "$CLIP_REACH_PREFIX.validation.${it.translationSuffix()}"
        }
        MaceClipReachEvidencePhase.entries.mapTo(this) {
            "$CLIP_REACH_PREFIX.phase.${it.translationSuffix()}"
        }
        MaceClipReachBlockReason.entries.mapTo(this) {
            "$CLIP_REACH_PREFIX.blockReason.${it.translationSuffix()}"
        }
        MaceClipReachSessionOutcome.entries.mapTo(this) {
            "$CLIP_REACH_PREFIX.sessionOutcome.${it.translationSuffix()}"
        }
        MaceClipReachReplanBlockReason.entries.mapTo(this) {
            "$CLIP_REACH_PREFIX.replanBlockReason.${it.translationSuffix()}"
        }
    }

    private fun Enum<*>.translationSuffix(): String = name.lowercase().split('_')
        .mapIndexed { index, part -> if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() } }
        .joinToString("")

    private fun readLocale(locale: String): JsonObject {
        return JsonParser.parseString(readLocaleSource(locale)).asJsonObject
    }

    private fun readLocaleSource(locale: String): String {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { InputStreamReader(it).readText() }
    }

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        const val MACE_KILL_PREFIX = "liquidbounce.module.maceKill"
        const val CLIP_REACH_PREFIX =
            "liquidbounce.module.maceKill.movement.packet.routing.clipReach"
        const val INSTANT_PREFIX =
            "liquidbounce.module.maceKill.movement.packet.routing.instant"
        val PLACEHOLDER_REGEX = Regex("%(?:\\d+\\$)?[a-zA-Z]")
        val TOP_LEVEL_KEY_REGEX = Regex("(?m)^\\s*\"((?:\\\\.|[^\"\\\\])+)\"\\s*:")
        val PREFIXES = setOf(
            MACE_KILL_PREFIX,
            "liquidbounce.module.fightBot.maceAutomation",
            "liquidbounce.command.maceclip",
        )
        val REQUIRED_KEYS = setOf(
            "liquidbounce.module.maceKill.description",
            "liquidbounce.module.maceKill.fallHeight.description",
            "liquidbounce.module.maceKill.targetDistance.description",
            "liquidbounce.module.maceKill.activation.description",
            "liquidbounce.module.maceKill.targetSource.description",
            "liquidbounce.module.maceKill.movement.targetSpeed.description",
            "liquidbounce.module.maceKill.movement.targetSpeed.warning",
            "liquidbounce.module.maceKill.movement.acceleration.description",
            "liquidbounce.module.maceKill.movement.deceleration.description",
            "liquidbounce.module.maceKill.movement.motion.stepDistance.description",
            "liquidbounce.module.maceKill.movement.packet.stepDistance.description",
            "liquidbounce.module.maceKill.movement.packet.stepDelay.description",
            "liquidbounce.module.maceKill.movement.packet.routing.description",
            "liquidbounce.module.maceKill.movement.packet.routing.aStar.extendedDescription",
            "liquidbounce.module.maceKill.movement.packet.routing.instant.extendedDescription",
            "liquidbounce.module.maceKill.movement.packet.routing.instant.primingPackets.description",
            "liquidbounce.module.maceKill.movement.packet.routing.instant.clearanceHeight.description",
            "liquidbounce.module.maceKill.movement.packet.routing.instant.maxPackets.description",
            "liquidbounce.module.maceKill.movement.packet.routing.instant.experimentalWarning",
            "liquidbounce.module.maceKill.movement.packet.routing.instant.groundSpoof.warning",
            "liquidbounce.module.maceKill.preview.renderPath.description",
            "liquidbounce.module.maceKill.messages.pathBlocked",
            "liquidbounce.module.maceKill.messages.targetUnreachable",
            "liquidbounce.module.maceKill.messages.routeRejected",
            "liquidbounce.module.maceKill.messages.correctionRecoveryFailed",
            "liquidbounce.module.maceKill.messages.instantCorrected",
            "liquidbounce.module.maceKill.messages.instantTimedOut",
            "liquidbounce.module.maceKill.messages.instantTargetLost",
            "liquidbounce.module.maceKill.messages.instantReplanRejected",
            "liquidbounce.module.maceKill.messages.instantPacketBudgetExceeded",
            "liquidbounce.module.fightBot.maceAutomation.description",
            "liquidbounce.command.maceclip.description",
            "liquidbounce.command.maceclip.subcommand.probe.description",
            "liquidbounce.command.maceclip.subcommand.status.description",
            "liquidbounce.command.maceclip.subcommand.abort.description",
        ) + maceClipCommandKeys()
        val RETIRED_KEYS = setOf(
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.description",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.extendedDescription",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.maxSpeed.description",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.minimumStepDelay.description",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.setbackBackoff.description",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.maxCost.description",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.diagonal.description",
            "liquidbounce.module.maceKill.movement.packet.routing.networkOptimized.lineOfSightShortcuts.description",
        )

        private fun maceClipCommandKeys(): Set<String> {
            val probeBase = "liquidbounce.command.maceclip.subcommand.probe.subcommand"
            val sharedParameters = listOf(
                "primingPackets",
                "packetShape",
                "clearance",
                "phaseDelayTicks",
                "terminalHoldTicks",
            )
            return buildSet {
                add("$probeBase.move.description")
                add("$probeBase.move.parameter.distance.description")
                sharedParameters.forEach { parameter ->
                    add("$probeBase.move.parameter.$parameter.description")
                    add("$probeBase.attack.parameter.$parameter.description")
                }
                add("$probeBase.attack.description")
                listOf(
                    "started",
                    "activeProbe",
                    "activeRemoteKillSession",
                    "unsafeContext",
                    "invalidContext",
                    "noTarget",
                    "routeRejected",
                    "loggingUnavailable",
                    "statusIdle",
                    "statusActive",
                    "abortRequested",
                    "abortIdle",
                ).forEach { result -> add("liquidbounce.command.maceclip.result.$result") }
                listOf(
                    "finiteDistance",
                    "distanceRange",
                    "finiteClearance",
                    "clearanceRange",
                    "packetShape",
                ).forEach { validation -> add("liquidbounce.command.maceclip.validation.$validation") }
            }
        }
    }
}
