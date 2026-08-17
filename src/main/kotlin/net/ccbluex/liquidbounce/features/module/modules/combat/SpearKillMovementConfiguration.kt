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
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventListener

/** Owns SpearKill's movement-mode schema independently from its attack runtime. */
internal class SpearKillMovementConfiguration(eventListener: EventListener?) {

    lateinit var motion: Motion
        private set
    lateinit var packet: Packet
        private set

    val choice = ModeValueGroup<SpearKillMovementChoice>(eventListener, "Movement", { 1 }) { parent ->
        arrayOf(
            Motion(parent).also { motion = it },
            Packet(parent).also { packet = it },
        )
    }
    val targetSpeed by choice.float(
        "TargetSpeed",
        SPEAR_KILL_NORMAL_MAX_SPEED,
        SPEAR_KILL_MIN_TARGET_SPEED..SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
        "blocks/tick",
    ).onChange { it.coerceIn(SPEAR_KILL_MIN_TARGET_SPEED, SPEAR_KILL_EXPERIMENTAL_MAX_SPEED) }
    val acceleration by choice.float(
        "Acceleration",
        SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
        SPEAR_KILL_MIN_SPEED_CHANGE..SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
        "blocks/tick²",
    ).onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED_CHANGE, SPEAR_KILL_EXPERIMENTAL_MAX_SPEED) }
    val deceleration by choice.float(
        "Deceleration",
        SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
        SPEAR_KILL_MIN_SPEED_CHANGE..SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
        "blocks/tick²",
    ).onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED_CHANGE, SPEAR_KILL_EXPERIMENTAL_MAX_SPEED) }

    internal sealed class SpearKillMovementChoice(
        name: String,
        aliases: List<String> = emptyList(),
        final override val parent: ModeValueGroup<SpearKillMovementChoice>,
    ) : Mode(name, aliases)

    internal class Motion(parent: ModeValueGroup<SpearKillMovementChoice>) : SpearKillMovementChoice(
        name = "Motion",
        parent = parent,
    ) {
        val stepDistance by float(
            "StepDistance",
            SPEAR_KILL_NORMAL_MAX_SPEED,
            SPEAR_KILL_MIN_SPEED..SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
            "blocks",
            aliases = listOf("StepsPerTeleport", "StepLimit"),
        ).onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED, SPEAR_KILL_EXPERIMENTAL_MAX_SPEED) }
    }

    internal class Packet(parent: ModeValueGroup<SpearKillMovementChoice>) : SpearKillMovementChoice(
        name = "Packet",
        aliases = listOf("PacketBoot", "Packet-Boot"),
        parent = parent,
    ) {
        lateinit var direct: Direct
            private set
        lateinit var aStar: AStar
            private set
        lateinit var networkOptimized: NetworkOptimized
            private set
        lateinit var instant: Instant
            private set

        val stepDistance by float(
            "StepDistance",
            SPEAR_KILL_ELYTRA_MAX_SPEED,
            SPEAR_KILL_MIN_SPEED..SPEAR_KILL_EXPERIMENTAL_MAX_SPEED,
            "blocks",
            aliases = listOf("StepsPerTeleport", "StepLimit"),
        ).onChange { it.coerceIn(SPEAR_KILL_MIN_SPEED, SPEAR_KILL_EXPERIMENTAL_MAX_SPEED) }
        val stepDelay by int(
            "StepDelay",
            0,
            0..SPEAR_KILL_MAX_WAIT_TICKS,
            "ticks",
            aliases = listOf("WaitBeforeTeleport", "WaitTicks"),
        )
        val routing = modes("Routing", 0) { routingParent ->
            arrayOf(
                Direct(routingParent).also { direct = it },
                AStar(routingParent).also { aStar = it },
                NetworkOptimized(routingParent).also { networkOptimized = it },
                Instant(routingParent).also { instant = it },
            )
        }
    }

    internal sealed class SpearKillRoutingChoice(
        name: String,
        aliases: List<String> = emptyList(),
        final override val parent: ModeValueGroup<SpearKillRoutingChoice>,
    ) : Mode(name, aliases)

    internal class Direct(parent: ModeValueGroup<SpearKillRoutingChoice>) : SpearKillRoutingChoice(
        name = "Direct",
        parent = parent,
    )

    internal class AStar(parent: ModeValueGroup<SpearKillRoutingChoice>) : SpearKillRoutingChoice(
        name = "AStar",
        aliases = listOf("Adaptive"),
        parent = parent,
    ) {
        val maxCost by int("MaxCost", 250, 50..500)
        val diagonal by boolean("Diagonal", false)
        val lineOfSightShortcuts by boolean("LineOfSightShortcuts", false)
    }

    internal class NetworkOptimized(
        parent: ModeValueGroup<SpearKillRoutingChoice>,
    ) : SpearKillRoutingChoice(
        name = "NetworkOptimized",
        aliases = listOf("Network", "LagOptimized", "Network-Optimized"),
        parent = parent,
    ) {
        val maxSpeed by float(
            "MaxSpeed",
            SPEAR_KILL_NORMAL_MAX_SPEED,
            SPEAR_KILL_MIN_SPEED..SPEAR_KILL_ELYTRA_MAX_SPEED,
            "blocks/tick",
        )
        val minimumStepDelay by int(
            "MinimumStepDelay",
            1,
            0..SPEAR_KILL_MAX_WAIT_TICKS,
            "ticks",
        )
        val setbackBackoff by int("SetbackBackoff", 40, 0..200, "ticks")
        val maxCost by int("MaxCost", 250, 50..500)
        val diagonal by boolean("Diagonal", true)
        val lineOfSightShortcuts by boolean("LineOfSightShortcuts", true)
    }

    internal class Instant(parent: ModeValueGroup<SpearKillRoutingChoice>) : SpearKillRoutingChoice(
        name = "Instant",
        parent = parent,
    ) {
        val maxPackets by int(
            "MaxPackets",
            SPEAR_KILL_INSTANT_DEFAULT_MAX_PACKETS,
            SPEAR_KILL_INSTANT_MIN_MAX_PACKETS..SPEAR_KILL_INSTANT_MAX_MAX_PACKETS,
            "packets/tick",
        )
    }
}

/** Matches vanilla's basic preconditions before SpearKill asks the server to start fall flying. */
internal fun canStartSpearKillElytraFlight(
    isFallFlying: Boolean,
    hasFlyingAbility: Boolean,
    isPassenger: Boolean,
    isOnClimbable: Boolean,
    isInWater: Boolean,
    hasLevitation: Boolean,
    isOnGround: Boolean,
    hasUsableElytra: Boolean,
): Boolean = hasUsableElytra && !hasFlyingAbility && !isPassenger && !isOnClimbable &&
    !isInWater && !hasLevitation && (isFallFlying || !isOnGround)

internal const val SPEAR_KILL_MIN_SPEED = 2f
internal const val SPEAR_KILL_MIN_TARGET_SPEED = 1f
internal const val SPEAR_KILL_MIN_SPEED_CHANGE = 0.1f
internal const val SPEAR_KILL_NORMAL_MAX_SPEED = 10f
internal const val SPEAR_KILL_ELYTRA_MAX_SPEED = 17.32f
internal const val SPEAR_KILL_EXPERIMENTAL_MAX_SPEED = 500f
internal const val SPEAR_KILL_MAX_WAIT_TICKS = 4
