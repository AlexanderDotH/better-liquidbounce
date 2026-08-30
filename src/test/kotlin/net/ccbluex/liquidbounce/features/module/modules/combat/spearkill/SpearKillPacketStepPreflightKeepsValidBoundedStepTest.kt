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

class SpearKillPacketStepPreflightKeepsValidBoundedStepTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }


    @Test
    fun `Packet step preflight keeps a valid bounded step and rejects unsafe packets`() {
        val origin = Vec3(4.0, 64.0, -2.0)
        val committedOffset = Vec3(1.0, 0.0, 0.0)
        val validCandidateOffset = Vec3(4.0, 2.0, 0.0)

        assertTrue(isSpearKillPacketStepClear(
            sessionOrigin = origin,
            committedOffset = committedOffset,
            candidateOffset = validCandidateOffset,
            maxStepLength = 4.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                from == origin.add(committedOffset) && to == origin.add(validCandidateOffset)
            },
        ))
        assertFalse(isSpearKillPacketStepClear(
            sessionOrigin = origin,
            committedOffset = committedOffset,
            candidateOffset = Vec3(6.0, 0.0, 0.0),
            maxStepLength = 4.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        ))
        assertFalse(isSpearKillPacketStepClear(
            sessionOrigin = origin,
            committedOffset = committedOffset,
            candidateOffset = validCandidateOffset,
            maxStepLength = 4.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
        ))
        assertFalse(isSpearKillPacketStepClear(
            sessionOrigin = origin,
            committedOffset = committedOffset,
            candidateOffset = Vec3(Double.NaN, 0.0, 0.0),
            maxStepLength = 4.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        ))
    }

    @Test
    fun `only the selected packet carries a pending SpearKill step`() {
        val committedOffset = Vec3(2.0, 0.0, -1.0)
        val pendingOffset = Vec3(5.0, 1.0, -1.0)

        assertEquals(
            pendingOffset,
            spearKillPacketVirtualOffset(
                carriesPendingStep = true,
                committedOffset = committedOffset,
                pendingOffset = pendingOffset,
            ),
        )
        assertEquals(
            committedOffset,
            spearKillPacketVirtualOffset(
                carriesPendingStep = false,
                committedOffset = committedOffset,
                pendingOffset = pendingOffset,
            ),
        )
    }

    @Test
    fun `AStar render path preserves the outbound route and is optional`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val firstWaypoint = Vec3(1.5, 64.0, 0.5)
        val secondWaypoint = Vec3(1.5, 69.0, 0.5)
        val renderPath = buildSpearKillAStarRenderPath(
            origin = origin,
            outboundWaypoints = listOf(firstWaypoint, firstWaypoint, secondWaypoint),
        )

        assertEquals(listOf(origin, firstWaypoint, secondWaypoint), renderPath)
        assertFalse(shouldRenderSpearKillAStarPath(
            previewEnabled = false,
            packetAStarEnabled = true,
            renderPathEnabled = true,
            renderPath = renderPath,
        ))
        assertFalse(shouldRenderSpearKillAStarPath(
            previewEnabled = true,
            packetAStarEnabled = false,
            renderPathEnabled = true,
            renderPath = renderPath,
        ))
        assertFalse(shouldRenderSpearKillAStarPath(
            packetAStarEnabled = true,
            renderPathEnabled = false,
            renderPath = renderPath,
        ))
        assertFalse(shouldRenderSpearKillAStarPath(
            packetAStarEnabled = true,
            renderPathEnabled = true,
            renderPath = listOf(origin),
        ))
        assertTrue(shouldRenderSpearKillAStarPath(
            previewEnabled = true,
            packetAStarEnabled = true,
            renderPathEnabled = true,
            renderPath = renderPath,
        ))
    }

    @Test
    fun `AStar path appearance preserves target Glow color and controls`() {
        val targetGlowColor = Color4b(12, 34, 56, 78)
        val targetGlowStyle = EspGlowStyle(
            radius = 21f,
            softness = 0.75f,
            intensity = 1.5f,
            coreSize = 2.25f,
            opacity = 0.4f,
        )

        val appearance = SpearKillAStarPathAppearance(targetGlowColor, targetGlowStyle)

        assertEquals(targetGlowColor, appearance.color)
        assertEquals(targetGlowColor, appearance.glowMaskColor)
        assertEquals(targetGlowStyle, appearance.style)
    }

    @Test
    fun `preview exposes colors only below their owning mode`() {
        val preview = ModuleSpearKill.inner
            .filterIsInstance<ToggleableValueGroup>()
            .single { it.name == "Preview" }
        val mode = preview.inner
            .filterIsInstance<ModeValueGroup<*>>()
            .single { it.name == "Mode" }

        assertEquals(listOf("Enabled", "RenderPath", "Mode"), preview.inner.map { it.name })
        assertFalse(preview.inner.single { it.name == "RenderPath" }.get() as Boolean)
        assertEquals("Box", mode.activeMode.name)
        assertEquals(
            mapOf(
                "Box" to listOf("FillColor", "OutlineColor"),
                "Glow" to listOf("GlowColor", "Radius", "Softness", "Intensity", "CoreSize", "Opacity"),
            ),
            mode.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
    }

    @Test
    fun `flat preview settings migrate into their owning modes`() {
        val legacy = JsonParser.parseString(
            """
            {
              "name": "Preview",
              "value": [
                { "name": "Enabled", "value": true },
                { "name": "Mode", "value": "Glow" },
                { "name": "FillColor", "value": 1140785152 },
                { "name": "OutlineColor", "value": -1476395009 },
                { "name": "GlowColor", "value": -65536 },
                { "name": "Radius", "value": 14.0 },
                { "name": "Softness", "value": 1.0 },
                { "name": "Intensity", "value": 1.0 },
                { "name": "CoreSize", "value": 0.83 },
                { "name": "Opacity", "value": 100 }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillPreviewConfig(legacy)

        val values = legacy.getAsJsonArray("value").map { it.asJsonObject }
        val mode = values.single { it["name"].asString == "Mode" }
        val choices = mode.getAsJsonObject("choices")

        assertEquals(listOf("Enabled", "Mode"), values.map { it["name"].asString })
        assertEquals("Glow", mode["active"].asString)
        assertEquals(1140785152, choices.previewSetting("Box", "FillColor").asInt)
        assertEquals(-1476395009, choices.previewSetting("Box", "OutlineColor").asInt)
        assertEquals(-65536, choices.previewSetting("Glow", "GlowColor").asInt)
        assertEquals(14f, choices.previewSetting("Glow", "Radius").asFloat)
        assertEquals(1f, choices.previewSetting("Glow", "Softness").asFloat)
        assertEquals(1f, choices.previewSetting("Glow", "Intensity").asFloat)
        assertEquals(0.83f, choices.previewSetting("Glow", "CoreSize").asFloat)
        assertEquals(100, choices.previewSetting("Glow", "Opacity").asInt)
    }
}
