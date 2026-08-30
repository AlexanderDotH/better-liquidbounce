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
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill

import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.ShapeFlag
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.block.WeightedEdge
import net.ccbluex.liquidbounce.utils.block.aStarShortestPath
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

class SpearKillExposesRequestedSettingOrderFreshDefaultsTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `SpearKill exposes the requested setting order and fresh defaults`() {
        assertEquals(
            listOf(
                "Hidden",
                "TargetDistance",
                "Activation",
                "TargetSource",
                "Movement",
                "SneakWhileMoving",
                "ElytraWhileMoving",
                "Preview",
            ),
            ModuleSpearKill.inner.dropWhile { it.name != "Hidden" }.map { it.name },
        )
        assertEquals(
            SpearKillActivationMode.Manual,
            ModuleSpearKill.inner.single { it.name == "Activation" }.get(),
        )
        assertEquals(
            SpearKillTargetSource.Crosshair,
            ModuleSpearKill.inner.single { it.name == "TargetSource" }.get(),
        )
        @Suppress("UNCHECKED_CAST")
        val targetSource = ModuleSpearKill.inner.single { it.name == "TargetSource" }
            as ChoiceListValue<SpearKillTargetSource>
        assertEquals(setOf("Crosshair", "Combat"), targetSource.choices.map { it.tag }.toSet())

        assertEquals(
            SpearKillMovementAssistMode.NONE,
            ModuleSpearKill.inner.single { it.name == "SneakWhileMoving" }.get(),
        )
        assertEquals(
            SpearKillMovementAssistMode.NONE,
            ModuleSpearKill.inner.single { it.name == "ElytraWhileMoving" }.get(),
        )
    }

    @Test
    fun `Movement nests routing-specific controls under the Routing choice`() {
        val configuration = SpearKillMovementConfiguration(null)
        assertMovementTopology(configuration)
        assertMovementRanges(configuration)
        assertSerializedMovement(configuration)
        assertPacketBootAliases(configuration)
    }

    private fun assertMovementTopology(configuration: SpearKillMovementConfiguration) {
        assertEquals("Packet", configuration.choice.activeMode.name)
        assertEquals(
            listOf("TargetSpeed", "Acceleration", "Deceleration"),
            configuration.choice.inner.map { it.name },
        )
        assertEquals(
            mapOf(
                "Motion" to listOf("StepDistance"),
                "Packet" to listOf("StepDistance", "StepDelay", "Routing"),
            ),
            configuration.choice.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
        assertEquals("Direct", configuration.packet.routing.activeMode.name)
        assertEquals(
            mapOf(
                "Direct" to emptyList(),
                "AStar" to listOf("MaxCost", "Diagonal", "LineOfSightShortcuts"),
                "NetworkOptimized" to listOf(
                    "MaxSpeed", "MinimumStepDelay", "SetbackBackoff", "MaxCost", "Diagonal",
                    "LineOfSightShortcuts",
                ),
                "Instant" to listOf("MaxPackets", "Strategy"),
            ),
            configuration.packet.routing.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertMovementRanges(configuration: SpearKillMovementConfiguration) {
        val movement = configuration.choice
        val motionStepDistance = configuration.motion.inner.single {
            it.name == "StepDistance"
        } as RangedValue<Float>
        val acceleration = movement.inner.single { it.name == "Acceleration" } as RangedValue<Float>
        val deceleration = movement.inner.single { it.name == "Deceleration" } as RangedValue<Float>
        val packetStepDistance = configuration.packet.inner.single {
            it.name == "StepDistance"
        } as RangedValue<Float>
        val packetStepDelay = configuration.packet.inner.single {
            it.name == "StepDelay"
        } as RangedValue<Int>
        val instantMaxPackets = configuration.packet.instant.inner.single {
            it.name == "MaxPackets"
        } as RangedValue<Int>
        assertEquals(10f, motionStepDistance.get())
        assertEquals(17.32f, packetStepDistance.get())
        assertEquals(500f, acceleration.get())
        assertEquals(500f, deceleration.get())
        assertEquals(0.1f..500f, acceleration.range)
        assertEquals(0.1f..500f, deceleration.range)
        assertEquals(2f..500f, motionStepDistance.range)
        assertEquals(2f..500f, packetStepDistance.range)
        assertEquals(listOf("StepsPerTeleport", "StepLimit"), motionStepDistance.aliases)
        assertEquals(listOf("StepsPerTeleport", "StepLimit"), packetStepDistance.aliases)
        assertEquals(0, packetStepDelay.get())
        assertEquals(0..4, packetStepDelay.range)
        assertEquals(listOf("WaitBeforeTeleport", "WaitTicks"), packetStepDelay.aliases)
        assertEquals(128, instantMaxPackets.get())
        assertEquals(2..512, instantMaxPackets.range)
    }

    private fun assertSerializedMovement(configuration: SpearKillMovementConfiguration) {
        val movement = configuration.choice
        val serializedMovement = fileGson.toJsonTree(movement, ModeValueGroup::class.java).asJsonObject
        val serializedRouting = serializedMovement.getAsJsonObject("choices")
            .choiceValue("Packet", "Routing")
        val serializedAStar = serializedRouting.getAsJsonObject("choices").getAsJsonObject("AStar")
        val serializedNetworkOptimized = serializedRouting.getAsJsonObject("choices")
            .getAsJsonObject("NetworkOptimized")
        val serializedInstant = serializedRouting.getAsJsonObject("choices")
            .getAsJsonObject("Instant")

        assertEquals(
            "Direct",
            serializedRouting["active"].asString,
        )
        assertEquals(
            setOf("Direct", "AStar", "NetworkOptimized", "Instant"),
            serializedRouting.getAsJsonObject("choices").keySet(),
        )
        assertEquals(250, serializedAStar.settingValue("MaxCost").asInt)
        assertFalse(serializedAStar.settingValue("Diagonal").asBoolean)
        assertFalse(serializedAStar.settingValue("LineOfSightShortcuts").asBoolean)
        assertEquals(10f, serializedNetworkOptimized.settingValue("MaxSpeed").asFloat)
        assertEquals(1, serializedNetworkOptimized.settingValue("MinimumStepDelay").asInt)
        assertEquals(40, serializedNetworkOptimized.settingValue("SetbackBackoff").asInt)
        assertEquals(250, serializedNetworkOptimized.settingValue("MaxCost").asInt)
        assertTrue(serializedNetworkOptimized.settingValue("Diagonal").asBoolean)
        assertTrue(serializedNetworkOptimized.settingValue("LineOfSightShortcuts").asBoolean)
        assertEquals(128, serializedInstant.settingValue("MaxPackets").asInt)
        assertEquals(
            10f,
            serializedMovement.getAsJsonObject("choices")
                .choiceValue("Motion", "StepDistance")["value"].asFloat,
        )
        assertEquals(
            17.32f,
            serializedMovement.getAsJsonObject("choices")
                .choiceValue("Packet", "StepDistance")["value"].asFloat,
        )
        assertEquals(500f, serializedMovement.settingValue("Acceleration").asFloat)
        assertEquals(500f, serializedMovement.settingValue("Deceleration").asFloat)
        assertEquals(
            0,
            serializedMovement.getAsJsonObject("choices")
                .choiceValue("Packet", "StepDelay")["value"].asInt,
        )
    }

    private fun assertPacketBootAliases(configuration: SpearKillMovementConfiguration) {
        val movement = configuration.choice
        try {
            movement.setByString("PacketBoot")
            assertEquals("Packet", movement.activeMode.name)
            movement.setByString("Packet-Boot")
            assertEquals("Packet", movement.activeMode.name)
        } finally {
            movement.restore()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `TargetSpeed is shared by both movement modes without a vanilla clamp`() {
        val movement = ModuleSpearKill.inner.single { it.name == "Movement" } as ModeValueGroup<*>
        val targetSpeed = movement.inner.single { it.name == "TargetSpeed" } as RangedValue<Float>

        assertEquals(10f, targetSpeed.get())
        assertEquals(1f..500f, targetSpeed.range)
        try {
            targetSpeed.set(500f)

            assertEquals(500f, targetSpeed.get())
        } finally {
            targetSpeed.restore()
        }
    }
}
