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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.EventListener

/** MaceKill's saved schema; execution is delegated to the remote-kill route runtime. */
internal class MaceKillMovementConfiguration(eventListener: EventListener?) {

    lateinit var motion: Motion
        private set
    lateinit var packet: Packet
        private set

    val choice = ModeValueGroup<MaceKillMovementChoice>(eventListener, "Movement", { 1 }) { parent ->
        arrayOf(
            Motion(parent).also { motion = it },
            Packet(parent).also { packet = it },
        )
    }
    val targetSpeed by choice.float(
        "TargetSpeed",
        MACE_KILL_NORMAL_MAX_SPEED,
        MACE_KILL_MIN_TARGET_SPEED..MACE_KILL_EXPERIMENTAL_MAX_SPEED,
        "blocks/tick",
    ).onChange { it.coerceIn(MACE_KILL_MIN_TARGET_SPEED, MACE_KILL_EXPERIMENTAL_MAX_SPEED) }
    val acceleration by choice.float(
        "Acceleration",
        MACE_KILL_EXPERIMENTAL_MAX_SPEED,
        MACE_KILL_MIN_SPEED_CHANGE..MACE_KILL_EXPERIMENTAL_MAX_SPEED,
        "blocks/tick²",
    ).onChange { it.coerceIn(MACE_KILL_MIN_SPEED_CHANGE, MACE_KILL_EXPERIMENTAL_MAX_SPEED) }
    val deceleration by choice.float(
        "Deceleration",
        MACE_KILL_EXPERIMENTAL_MAX_SPEED,
        MACE_KILL_MIN_SPEED_CHANGE..MACE_KILL_EXPERIMENTAL_MAX_SPEED,
        "blocks/tick²",
    ).onChange { it.coerceIn(MACE_KILL_MIN_SPEED_CHANGE, MACE_KILL_EXPERIMENTAL_MAX_SPEED) }

    internal sealed class MaceKillMovementChoice(
        name: String,
        aliases: List<String> = emptyList(),
        final override val parent: ModeValueGroup<MaceKillMovementChoice>,
    ) : Mode(name, aliases)

    internal class Motion(parent: ModeValueGroup<MaceKillMovementChoice>) : MaceKillMovementChoice(
        name = "Motion",
        parent = parent,
    ) {
        val stepDistance by float(
            "StepDistance",
            MACE_KILL_NORMAL_MAX_SPEED,
            MACE_KILL_MIN_SPEED..MACE_KILL_EXPERIMENTAL_MAX_SPEED,
            "blocks",
            aliases = listOf("StepsPerTeleport", "StepLimit"),
        ).onChange { it.coerceIn(MACE_KILL_MIN_SPEED, MACE_KILL_EXPERIMENTAL_MAX_SPEED) }
    }

    internal class Packet(parent: ModeValueGroup<MaceKillMovementChoice>) : MaceKillMovementChoice(
        name = "Packet",
        aliases = listOf("PacketBoot", "Packet-Boot"),
        parent = parent,
    ) {
        lateinit var direct: Direct
            private set
        lateinit var aStar: AStar
            private set
        lateinit var instant: Instant
            private set
        val stepDistance by float(
            "StepDistance",
            MACE_KILL_ELYTRA_MAX_SPEED,
            MACE_KILL_MIN_SPEED..MACE_KILL_EXPERIMENTAL_MAX_SPEED,
            "blocks",
            aliases = listOf("StepsPerTeleport", "StepLimit"),
        ).onChange { it.coerceIn(MACE_KILL_MIN_SPEED, MACE_KILL_EXPERIMENTAL_MAX_SPEED) }
        val stepDelay by int(
            "StepDelay",
            0,
            0..MACE_KILL_MAX_WAIT_TICKS,
            "ticks",
            aliases = listOf("WaitBeforeTeleport", "WaitTicks"),
        )
        val routing = modes("Routing", 0) { routingParent ->
            arrayOf(
                Direct(routingParent).also { direct = it },
                AStar(routingParent).also { aStar = it },
                Instant(routingParent).also { instant = it },
            )
        }
    }

    internal sealed class MaceKillRoutingChoice(
        name: String,
        aliases: List<String> = emptyList(),
        final override val parent: ModeValueGroup<MaceKillRoutingChoice>,
    ) : Mode(name, aliases)

    internal class Direct(parent: ModeValueGroup<MaceKillRoutingChoice>) : MaceKillRoutingChoice(
        name = "Direct",
        parent = parent,
    )

    internal class AStar(parent: ModeValueGroup<MaceKillRoutingChoice>) : MaceKillRoutingChoice(
        name = "AStar",
        aliases = listOf("Adaptive"),
        parent = parent,
    ) {
        val maxCost by int("MaxCost", 500, 50..500)
        val diagonal by boolean("Diagonal", false)
        val lineOfSightShortcuts by boolean("LineOfSightShortcuts", false)
    }

    internal class Instant(parent: ModeValueGroup<MaceKillRoutingChoice>) : MaceKillRoutingChoice(
        name = "Instant",
        parent = parent,
    ) {
        val primingPackets by int("PrimingPackets", 9, 0..18, "packets")
        val clearanceHeight by int("ClearanceHeight", 99, 4..128, "blocks")
        val maxPackets by int(
            "MaxPackets",
            128,
            6..512,
            "packets",
        )
    }
}
