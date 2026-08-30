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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.utils.collection.Filter

internal class InteractableRoutingConfiguration : ValueGroup("Routing") {
    private val maxCost by int("MaxCost", 4096, 256..16384)
    private val diagonal by boolean("Diagonal", true)
    private val lineOfSightShortcuts by boolean("LineOfSightShortcuts", true)
    private val stepDistance by float("StepDistance", 9.5f, 1f..20f, "blocks")
    private val stepDelay by int("StepDelay", 0, 0..20, "ticks")
    private val nodesPerTick by int("NodesPerTick", 750, 50..5000, "nodes")
    private val renderPath by boolean("RenderPath", true)

    fun capture() = InteractableRoutingSettings(
        maxCost, diagonal, lineOfSightShortcuts, stepDistance.toDouble(), stepDelay, nodesPerTick, renderPath,
    )
}

internal class InteractableSurfaceFallbackConfiguration(parent: EventListener) : ToggleableValueGroup(
    parent,
    "SurfaceFallback",
    enabled = true,
) {
    private val maxRise by int("MaxRise", 128, 1..384, "blocks")
    private val horizontalSearch by int("HorizontalSearch", 48, 1..128, "blocks")
    private val maxClipDistance by int("MaxClipDistance", 30, 4..30, "blocks")
    private val protectBedrock by boolean("DoNotClipAroundBedrock", true)
    private val transportConfiguration = InteractableVClipConfiguration(this)
    private val transport = tree(transportConfiguration.choice)

    fun capture() = InteractableSurfaceFallbackSettings(
        enabled,
        maxRise,
        horizontalSearch,
        maxClipDistance,
        protectBedrock,
        transportConfiguration.capture(),
    )
}

private class InteractableVClipConfiguration(parent: EventListener) {
    lateinit var vanilla: Vanilla
        private set
    lateinit var folia: Folia
        private set

    val choice = ModeValueGroup<Choice>(parent, "VClip", { 0 }) { choiceParent ->
        arrayOf(
            Vanilla(choiceParent).also { vanilla = it },
            Folia(choiceParent).also { folia = it },
        )
    }

    fun capture(): InteractableVClipSettings = when (choice.activeMode) {
        vanilla -> InteractableVClipSettings.Vanilla(vanilla.paperBypass, vanilla.fullPacket)
        folia -> InteractableVClipSettings.Folia(folia.movementPackets, folia.fullPacket)
        else -> error("Unsupported Interactable VClip profile ${choice.activeMode.name}")
    }

    sealed class Choice(name: String, final override val parent: ModeValueGroup<Choice>) : Mode(name)

    class Vanilla(parent: ModeValueGroup<Choice>) : Choice("Vanilla", parent) {
        val paperBypass by boolean("PaperBypass", false)
        val fullPacket by boolean("FullPacket", false)
    }

    class Folia(parent: ModeValueGroup<Choice>) : Choice("Folia", parent) {
        val movementPackets by int("MovementPackets", 5, 1..5, "packets")
        val fullPacket by boolean("FullPacket", false)
    }
}
