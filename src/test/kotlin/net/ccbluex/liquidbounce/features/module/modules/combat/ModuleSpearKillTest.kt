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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("LargeClass")
class ModuleSpearKillTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST", "LongMethod")
    fun `Movement nests AStar packet controls with safe defaults`() {
        val configuration = SpearKillMovementConfiguration(null)
        val movement = configuration.choice

        assertEquals("Motion", movement.activeMode.name)
        assertEquals(
            mapOf(
                "Motion" to listOf("StepLimit"),
                "Packet" to listOf("StepLimit", "WaitTicks", "Elytra", "AStar"),
            ),
            movement.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
        assertEquals(
            listOf("Enabled", "MaxSpeed"),
            configuration.packet.elytra.inner.map { it.name },
        )
        assertEquals(
            listOf("Enabled", "MaxCost", "Diagonal", "RenderPath"),
            configuration.packet.aStar.inner.map { it.name },
        )
        val motionStepLimit = configuration.motion.inner.single { it.name == "StepLimit" } as RangedValue<Float>
        val packetStepLimit = configuration.packet.inner.single { it.name == "StepLimit" } as RangedValue<Float>
        val packetWaitTicks = configuration.packet.inner.single { it.name == "WaitTicks" } as RangedValue<Int>
        assertEquals(10f, motionStepLimit.get())
        assertEquals(17.32f, packetStepLimit.get())
        assertEquals(2f..10f, motionStepLimit.range)
        assertEquals(2f..17.32f, packetStepLimit.range)
        assertEquals(0, packetWaitTicks.get())
        assertEquals(0..4, packetWaitTicks.range)

        val serializedMovement = fileGson.toJsonTree(movement, ModeValueGroup::class.java).asJsonObject
        val serializedElytra = serializedMovement.getAsJsonObject("choices")
            .choiceValue("Packet", "Elytra")
        val serializedAStar = serializedMovement.getAsJsonObject("choices")
            .choiceValue("Packet", "AStar")

        assertFalse(serializedElytra.settingValue("Enabled").asBoolean)
        assertEquals(17.32f, serializedElytra.settingValue("MaxSpeed").asFloat)
        assertFalse(serializedAStar.settingValue("Enabled").asBoolean)
        assertEquals(250, serializedAStar.settingValue("MaxCost").asInt)
        assertFalse(serializedAStar.settingValue("Diagonal").asBoolean)
        assertFalse(serializedAStar.settingValue("RenderPath").asBoolean)
        assertEquals(
            10f,
            serializedMovement.getAsJsonObject("choices").choiceValue("Motion", "StepLimit")["value"].asFloat,
        )
        assertEquals(
            17.32f,
            serializedMovement.getAsJsonObject("choices").choiceValue("Packet", "StepLimit")["value"].asFloat,
        )
        assertEquals(
            0,
            serializedMovement.getAsJsonObject("choices").choiceValue("Packet", "WaitTicks")["value"].asInt,
        )

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
    fun `normal and Elytra speed sliders expose their server-safe caps`() {
        val maxSpeed = ModuleSpearKill.inner.single { it.name == "MaxSpeed" } as RangedValue<Float>
        val configuration = SpearKillMovementConfiguration(null)
        val elytraMaxSpeed = configuration.packet.elytra.inner.single { it.name == "MaxSpeed" } as RangedValue<Float>

        assertEquals(10f, maxSpeed.get())
        assertEquals(2f..10f, maxSpeed.range)
        assertEquals(17.32f, elytraMaxSpeed.get())
        assertEquals(2f..17.32f, elytraMaxSpeed.range)
        try {
            maxSpeed.set(11f)
            elytraMaxSpeed.set(18f)

            assertEquals(10f, maxSpeed.get())
            assertEquals(17.32f, elytraMaxSpeed.get())
        } finally {
            maxSpeed.restore()
            elytraMaxSpeed.restore()
        }
    }

    @Test
    fun `flat Movement values migrate to canonical nested choices`() {
        mapOf(
            "Motion" to "Motion",
            "Packet" to "Packet",
            "PacketBoot" to "Packet",
            "Packet-Boot" to "Packet",
        ).forEach { (savedName, expectedActive) ->
            val legacy = legacySpearKillMovementConfig(savedName)

            migrateLegacySpearKillMovementConfig(legacy)

            val movement = legacy.spearKillMovement()
            val choices = movement.getAsJsonObject("choices")
            assertEquals(expectedActive, movement["active"].asString)
            assertEquals(setOf("Motion", "Packet"), choices.keySet())
            assertEquals(emptyList<String>(), choices.choiceValues("Motion"))
            assertEquals(emptyList<String>(), choices.choiceValues("Packet"))
            assertEquals(listOf("Movement"), legacy.getAsJsonArray("value").map { it.asJsonObject["name"].asString })
        }
    }

    @Test
    fun `nested Movement config remains unchanged during migration`() {
        val nested = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [
                {
                  "name": "Movement",
                  "active": "Packet",
                  "value": [],
                  "choices": {
                    "Motion": { "name": "Motion", "value": [] },
                    "Packet": {
                      "name": "Packet",
                      "value": [
                        {
                          "name": "AStar",
                          "value": [
                            { "name": "Enabled", "value": true },
                            { "name": "MaxCost", "value": 321 },
                            { "name": "Diagonal", "value": true }
                          ]
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject
        val original = nested.deepCopy()

        migrateLegacySpearKillMovementConfig(nested)

        assertEquals(original, nested)
    }

    @Test
    fun `legacy AStar wait moves to the shared Packet wait idempotently`() {
        val nested = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [
                {
                  "name": "Movement",
                  "active": "Packet",
                  "value": [],
                  "choices": {
                    "Motion": { "name": "Motion", "value": [] },
                    "Packet": {
                      "name": "Packet",
                      "value": [
                        { "name": "WaitTicks", "value": 1 },
                        {
                          "name": "AStar",
                          "value": [
                            { "name": "Enabled", "value": true },
                            { "name": "WaitTicks", "value": 3 },
                            { "name": "MaxCost", "value": 321 }
                          ]
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillMovementConfig(nested)

        val packetValues = nested.spearKillMovement().getAsJsonObject("choices")
            .getAsJsonObject("Packet").getAsJsonArray("value").map { it.asJsonObject }
        val aStar = packetValues.single { it["name"].asString == "AStar" }
        assertEquals(3, packetValues.single { it["name"].asString == "WaitTicks" }["value"].asInt)
        assertTrue(aStar.getAsJsonArray("value").none { it.asJsonObject["name"].asString == "WaitTicks" })

        val once = nested.deepCopy()
        migrateLegacySpearKillMovementConfig(nested)
        assertEquals(once, nested)
    }

    @Test
    fun `AStar target eligibility bypasses direct gates only when enabled`() {
        assertTrue(isSpearKillAStarTargetEligible(
            hasLineOfSight = true,
            hasClearDirectTravel = true,
            packetAStarEnabled = false,
        ))
        assertFalse(isSpearKillAStarTargetEligible(
            hasLineOfSight = false,
            hasClearDirectTravel = true,
            packetAStarEnabled = false,
        ))
        assertFalse(isSpearKillAStarTargetEligible(
            hasLineOfSight = true,
            hasClearDirectTravel = false,
            packetAStarEnabled = false,
        ))
        assertTrue(isSpearKillAStarTargetEligible(
            hasLineOfSight = false,
            hasClearDirectTravel = false,
            packetAStarEnabled = true,
        ))
    }

    @Test
    fun `SpearKill raises the spear pose only while it owns the attack`() {
        assertFalse(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = false,
            attackRequested = false,
            isUsingSpear = false,
        ))
        assertTrue(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = true,
            attackRequested = false,
            isUsingSpear = false,
        ))
        assertTrue(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = false,
            attackRequested = true,
            isUsingSpear = false,
        ))
        assertTrue(shouldRaiseSpearKillAnimation(
            spearKillRunning = true,
            holdingSpear = true,
            attackPathActive = false,
            attackRequested = false,
            isUsingSpear = true,
        ))
        assertFalse(shouldRaiseSpearKillAnimation(
            spearKillRunning = false,
            holdingSpear = true,
            attackPathActive = true,
            attackRequested = true,
            isUsingSpear = true,
        ))
    }

    @Test
    fun `SpearKill animation is a client-side charged pose snap`() {
        assertEquals(3f, spearKillAnimationTicks(shouldRaise = true, delayTicks = 3, originalTicks = 0f))
        assertEquals(3f, spearKillAnimationTicks(shouldRaise = true, delayTicks = 3, originalTicks = 8f))
        assertEquals(1f, spearKillAnimationTicks(shouldRaise = false, delayTicks = 3, originalTicks = 1f))
        assertTrue(shouldAnimateSpearKillUseItem(shouldRaise = true, isUsingItem = false))
        assertEquals(
            InteractionHand.MAIN_HAND,
            spearKillRaisedHand(
                shouldRaise = true,
                mainHandIsSpear = true,
                offHandIsSpear = false,
                isUsingItem = false,
                usedHand = InteractionHand.OFF_HAND,
            ),
        )
    }

    @Test
    fun `AStar route planner uses the configured diagonal option with bounded search defaults`() {
        val planner = SpearKillAStarRoutePlanner(allowDiagonal = false, maxCost = 250)

        assertFalse(planner.allowDiagonal)
        assertEquals(250, planner.maxCost)
        assertEquals(500, planner.maxIterations)
        assertEquals(1.0, planner.stopRange)
    }

    @Test
    fun `AStar planner accepts an empty route when already in the approach block`() {
        val planner = SpearKillAStarRoutePlanner(allowDiagonal = false, maxCost = 250)

        assertEquals(
            emptyList<Vec3>(),
            planner.plan(
                origin = Vec3(5.1, 64.0, 8.1),
                destination = Vec3(5.9, 64.0, 8.9),
            ),
        )
    }

    @Test
    fun `AStar attack approach creates a long straight run-up with valid spear stand-off`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
        )!!

        assertVec3Equals(Vec3(0.75, 64.0, 0.0), approach.plannerGoal, 1e-9)
        assertVec3Equals(Vec3(7.75, 64.0, 0.0), approach.terminalWaypoint, 1e-9)
        assertEquals(7.0, approach.plannerGoal.distanceTo(approach.terminalWaypoint), 1e-9)
        assertEquals(2.25, approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint), 1e-9)
    }

    @Test
    fun `AStar attack approach projects its terminal straight onto the horizontal plane`() {
        val hitPoint = Vec3(10.0, 72.0, 5.0)
        val eyeOffset = Vec3(0.0, 1.62, 0.0)
        val direction = Vec3(6.0, 8.0, 3.0)
        val horizontal = Vec3(direction.x, 0.0, direction.z).normalize()

        val approach = createSpearKillAStarAttackApproach(hitPoint, eyeOffset, direction)!!

        assertVec3Equals(hitPoint.subtract(horizontal.scale(9.25)).subtract(eyeOffset), approach.plannerGoal, 1e-9)
        assertVec3Equals(
            hitPoint.subtract(horizontal.scale(2.25)).subtract(eyeOffset),
            approach.terminalWaypoint,
            1e-9,
        )
        val terminalStraight = approach.terminalWaypoint.subtract(approach.plannerGoal)
        assertEquals(7.0, terminalStraight.length(), 1e-9)
        assertTrue(terminalStraight.x > 0.0)
        assertEquals(0.0, terminalStraight.y, 1e-9)
        assertTrue(terminalStraight.z > 0.0)
    }

    @Test
    fun `AStar terminal lunge keeps a reliable strike gap and one full default-speed packet`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
        )!!
        val outbound = buildSpearKillAStarOutboundMovements(
            origin = approach.plannerGoal,
            waypoints = listOf(approach.terminalWaypoint),
            maxSpeed = effectiveSpearKillPacketSpeed(7.0),
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(2.25, approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint), 1e-9)
        assertTrue(approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint) > 2.0)
        assertTrue(approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint) <= 4.5)
        assertEquals(1, outbound.size)
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), outbound.single(), 1e-9)
    }

    @Test
    fun `AStar terminal lunge matches the configured step size while preserving the strike gap`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = 3.0,
        )!!
        val outbound = buildSpearKillAStarOutboundMovements(
            origin = approach.plannerGoal,
            waypoints = listOf(approach.terminalWaypoint),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(2.25, approach.terminalWaypoint.add(eyeOffset).distanceTo(hitPoint), 1e-9)
        assertEquals(1, outbound.size)
        assertEquals(3.0, outbound.single().length(), 1e-9)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), outbound.single(), 1e-9)
    }

    @Test
    fun `close AStar target routes backward before one full StepLimit terminal hit`() {
        val origin = Vec3(5.0, 64.0, 0.0)
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = 7.0,
        )!!
        val route = buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(approach.plannerGoal, approach.terminalWaypoint),
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertVec3Equals(Vec3(0.75, 64.0, 0.0), approach.plannerGoal, 1e-9)
        assertVec3Equals(Vec3(7.75, 64.0, 0.0), approach.terminalWaypoint, 1e-9)
        assertTrue(route.outboundMovements.all { it.length() <= 7.0 })
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), route.outboundMovements.last(), 1e-9)
        assertVec3Equals(
            approach.terminalWaypoint.subtract(origin),
            route.outboundMovements.fold(Vec3.ZERO, Vec3::add),
            1e-9,
        )
        assertVec3Equals(Vec3.ZERO, route.roundTripMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar accepts only a full StepLimit terminal movement matching the attack approach`() {
        val approach = SpearKillAStarAttackApproach(
            plannerGoal = Vec3(0.0, 64.0, 0.0),
            terminalWaypoint = Vec3(7.0, 64.0, 0.0),
        )

        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(7.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(0.0, 0.0, 7.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
    }

    @Test
    fun `AStar attack approach offers lateral long-lunge alternatives`() {
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(10.0, 64.0, 0.0, 11.0, 66.0, 1.0),
            targetEyePosition = Vec3(10.5, 65.5, 0.5),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
        )

        assertTrue(approaches.size >= 3)
        assertTrue(approaches.first().terminalWaypoint.subtract(approaches.first().plannerGoal).x > 0.0)
        assertTrue(approaches.drop(1).any { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).z != 0.0
        })
        assertTrue(approaches.all { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).y == 0.0
        })
    }

    @Test
    fun `AStar terminal candidates stay horizontal even for a target directly above`() {
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(-0.5, 70.0, -0.5, 0.5, 72.0, 0.5),
            targetEyePosition = Vec3(0.0, 71.5, 0.0),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(0.0, 1.0, 0.0),
        )

        assertEquals(4, approaches.size)
        assertTrue(approaches.all { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).y == 0.0
        })
    }

    @Test
    fun `AStar skips a blocked primary lunge and keeps a lateral alternative`() {
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(10.0, 64.0, 0.0, 11.0, 66.0, 1.0),
            targetEyePosition = Vec3(10.5, 65.5, 0.5),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
        )

        val usable = filterSpearKillAStarApproachesByTerminalClearance(
            approaches = approaches,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                to.subtract(from).x == 0.0
            },
        )

        assertTrue(usable.isNotEmpty())
        assertTrue(usable.all { approach -> approach.terminalWaypoint.subtract(approach.plannerGoal).x == 0.0 })
    }

    @Test
    fun `Packet speed uses the normal or Elytra server-safe cap`() {
        assertEquals(7.0, effectiveSpearKillPacketSpeed(7.0), 1e-9)
        assertEquals(10.0, effectiveSpearKillPacketSpeed(10.0), 1e-9)
        assertEquals(10.0, effectiveSpearKillPacketSpeed(20.0), 1e-9)
        assertEquals(17.32, effectiveSpearKillPacketSpeed(20.0, elytra = true), 1e-9)
    }

    @Test
    fun `StepLimit caps normal and Elytra Packet movement independently`() {
        assertEquals(3.0, effectiveSpearKillStepLimit(30.0, 3.0, packetMode = false), 1e-9)
        assertEquals(3.0, effectiveSpearKillStepLimit(30.0, 3.0, packetMode = true), 1e-9)
        assertEquals(3.0, effectiveSpearKillStepLimit(3.0, 30.0, packetMode = false), 1e-9)
        assertEquals(10.0, effectiveSpearKillStepLimit(30.0, 30.0, packetMode = true), 1e-9)
        assertEquals(
            17.32,
            effectiveSpearKillStepLimit(30.0, 30.0, packetMode = true, packetElytra = true),
            1e-9,
        )
    }

    @Test
    fun `Packet transport uses Elytra speed only when safe flight is available`() {
        val normal = resolveSpearKillPacketTransport(
            elytraEnabled = true,
            elytraReady = false,
            normalMaxSpeed = 10.0,
            elytraMaxSpeed = 17.32,
            configuredStepLimit = 17.32,
        )
        val elytra = resolveSpearKillPacketTransport(
            elytraEnabled = true,
            elytraReady = true,
            normalMaxSpeed = 10.0,
            elytraMaxSpeed = 17.32,
            configuredStepLimit = 17.32,
        )

        assertFalse(normal.elytra)
        assertEquals(10.0, normal.stepLimit, 1e-9)
        assertTrue(elytra.elytra)
        assertEquals(17.32, elytra.stepLimit, 1e-9)
    }

    @Test
    fun `Elytra flight only starts from a valid airborne state`() {
        assertTrue(canStartSpearKillElytraFlight(
            isFallFlying = false,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = false,
            hasUsableElytra = true,
        ))
        assertTrue(canStartSpearKillElytraFlight(
            isFallFlying = true,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = false,
            hasUsableElytra = true,
        ))
        assertFalse(canStartSpearKillElytraFlight(
            isFallFlying = false,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = true,
            hasUsableElytra = true,
        ))
        assertFalse(canStartSpearKillElytraFlight(
            isFallFlying = false,
            hasFlyingAbility = false,
            isPassenger = false,
            isOnClimbable = false,
            isInWater = false,
            hasLevitation = false,
            isOnGround = false,
            hasUsableElytra = false,
        ))
    }

    @Test
    fun `long full XYZ terminal straight is safely split and reverses exactly`() {
        val origin = Vec3(0.0, 64.0, 0.0)
        val terminalWaypoint = Vec3(12.0, 73.0, 6.0)
        val movements = buildSpearKillAStarPacketMovements(
            origin = origin,
            outboundWaypoints = listOf(terminalWaypoint),
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val outbound = movements.dropLast(1).take(movements.dropLast(1).size / 2)
        val inbound = movements.dropLast(1).drop(outbound.size)

        assertTrue(outbound.size > 1)
        assertTrue(outbound.all { it.length() <= 7.0 })
        assertTrue(outbound.all { it.x > 0.0 && it.y > 0.0 && it.z > 0.0 })
        assertVec3Equals(terminalWaypoint.subtract(origin), outbound.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertEquals(outbound.asReversed().map { it.scale(-1.0) }, inbound)
        assertVec3Equals(Vec3.ZERO, movements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar long edge uses full fixed steps followed by its remainder`() {
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(2.0, 0.0, 0.0), Vec3(12.0, 0.0, 0.0)),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val expectedOutbound = listOf(
            Vec3(2.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(expectedOutbound, route.outboundMovements)
        assertEquals(
            expectedOutbound + expectedOutbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            route.roundTripMovements,
        )
    }

    @Test
    fun `Elytra AStar long edge uses two full safe steps followed by its remainder`() {
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(40.0, 0.0, 0.0)),
            maxSpeed = 17.32,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val outbound = route.outboundMovements

        assertEquals(3, outbound.size)
        assertEquals(17.32, outbound[0].length(), 1e-9)
        assertEquals(17.32, outbound[1].length(), 1e-9)
        assertEquals(5.36, outbound[2].length(), 1e-9)
        assertTrue(outbound.all { it.length() <= 17.32 })
        assertEquals(
            outbound.asReversed().map { it.scale(-1.0) },
            route.roundTripMovements.drop(outbound.size).dropLast(1),
        )
    }

    @Test
    fun `AStar follow replans moved targets at a bounded interval`() {
        val planned = Vec3(10.0, 64.0, 4.0)

        assertFalse(shouldReplanSpearKillAStarTarget(planned, planned.add(0.1, 0.0, 0.0), 20))
        assertTrue(shouldReplanSpearKillAStarTarget(planned, planned.add(1.0, 0.0, 0.0), 1))
        assertTrue(shouldReplanSpearKillAStarTarget(planned, planned.add(0.0, 1.0, 0.0), 1))
    }

    @Test
    fun `AStar compacts a clear straight route to MaxSpeed while preserving the final lunge`() {
        val origin = Vec3(0.5, 64.0, 0.5)
        val route = (1..7).map { Vec3(it + 0.5, 64.0, 0.5) }
        val compacted = simplifySpearKillAStarWaypoints(
            origin = origin,
            waypoints = route,
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )
        val terminalLungeEndpoint = Vec3(8.5, 64.0, 0.5)
        val movements = buildSpearKillAStarPacketMovements(
            origin = origin,
            outboundWaypoints = compacted + terminalLungeEndpoint,
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(1, compacted.size)
        assertVec3Equals(Vec3(7.5, 64.0, 0.5), compacted.single(), 1e-9)
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), movements[0], 1e-9)
        assertVec3Equals(Vec3(1.0, 0.0, 0.0), movements[1], 1e-9)
        assertVec3Equals(Vec3.ZERO, movements.last(), 1e-9)
    }

    @Test
    fun `AStar retains intermediate nodes when a long shortcut is blocked`() {
        val origin = Vec3(0.5, 64.0, 0.5)
        val route = (1..4).map { Vec3(it + 0.5, 64.0, 0.5) }
        val compacted = simplifySpearKillAStarWaypoints(
            origin = origin,
            waypoints = route,
            maxSpeed = 7.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to -> from.distanceTo(to) <= 1.0 },
        )

        assertEquals(route, compacted)
    }

    @Test
    fun `AStar packet route preserves outward order expands vertical travel and reverses exactly`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val firstWaypoint = Vec3(1.5, 64.0, 0.5)
        val secondWaypoint = Vec3(1.5, 69.0, 0.5)
        val movements = buildSpearKillAStarPacketMovements(
            origin = origin,
            outboundWaypoints = listOf(firstWaypoint, secondWaypoint),
            maxSpeed = 2.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val deltas = movements.dropLast(1)
        val half = deltas.size / 2
        var virtualPosition = origin
        val outboundPositions = buildList {
            for (movement in deltas.take(half)) {
                virtualPosition = virtualPosition.add(movement)
                add(virtualPosition)
            }
        }

        assertVec3Equals(Vec3.ZERO, movements.last(), 1e-9)
        assertTrue(deltas.all { it.length() <= 2.0 })
        assertVec3Equals(firstWaypoint, outboundPositions.first(), 1e-9)
        assertVec3Equals(secondWaypoint, outboundPositions.last(), 1e-9)
        assertTrue(deltas.take(half).count { it.y != 0.0 } > 1)
        for (index in 0 until half) {
            assertVec3Equals(deltas[index].scale(-1.0), deltas[deltas.lastIndex - index], 1e-9)
        }
        assertVec3Equals(Vec3.ZERO, deltas.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar packet route rejects empty and collision-blocked routes`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val waypoint = Vec3(1.5, 64.0, 0.5)

        assertEquals(
            null,
            buildSpearKillAStarPacketMovements(
                origin = origin,
                outboundWaypoints = emptyList(),
                maxSpeed = 2.0,
                segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            ),
        )
        assertEquals(
            null,
            buildSpearKillAStarPacketMovements(
                origin = origin,
                outboundWaypoints = listOf(waypoint),
                maxSpeed = 2.0,
                segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
            ),
        )
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

        assertEquals(listOf("Enabled", "Mode"), preview.inner.map { it.name })
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

    @Test
    fun `box-only preview settings migrate with box active`() {
        val legacy = JsonParser.parseString(
            """
            {
              "name": "Preview",
              "value": [
                { "name": "Enabled", "value": false },
                { "name": "FillColor", "value": 123 },
                { "name": "OutlineColor", "value": 456 }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillPreviewConfig(legacy)

        val mode = legacy.getAsJsonArray("value")
            .map { it.asJsonObject }
            .single { it["name"].asString == "Mode" }
        val choices = mode.getAsJsonObject("choices")

        assertEquals("Box", mode["active"].asString)
        assertEquals(123, choices.previewSetting("Box", "FillColor").asInt)
        assertEquals(456, choices.previewSetting("Box", "OutlineColor").asInt)
    }

    @Test
    fun `nested preview settings are not migrated twice`() {
        val nested = JsonParser.parseString(
            """
            {
              "name": "Preview",
              "value": [
                { "name": "Enabled", "value": true },
                {
                  "name": "Mode",
                  "active": "Glow",
                  "value": [],
                  "choices": {
                    "Box": { "name": "Box", "value": [] },
                    "Glow": { "name": "Glow", "value": [{ "name": "CoreSize", "value": 0.83 }] }
                  }
                }
              ]
            }
            """.trimIndent(),
        ).asJsonObject
        val original = nested.deepCopy()

        migrateLegacySpearKillPreviewConfig(nested)

        assertEquals(original, nested)
    }

    @Test
    fun `target selection accepts an entity just inside the widened three dimensional ray`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(10.0, 1.6, 0.0)
        val entityBox = AABB(4.0, 0.0, 0.30, 4.6, 1.8, 0.90)

        assertFalse(entityBox.clip(eye, lookEnd).isPresent)
        val priority = spearKillLookRayPriority(entityBox, eye, lookEnd, hitboxMargin = 0.35)!!

        assertFalse(priority.directlyHovered)
    }

    @Test
    fun `target selection rejects an entity beyond the widened ray margin`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(10.0, 1.6, 0.0)
        val entityBox = AABB(4.0, 0.0, 0.36, 4.6, 1.8, 0.96)

        assertNull(spearKillLookRayPriority(entityBox, eye, lookEnd, hitboxMargin = 0.35))
    }

    @Test
    fun `AStar uses a distance-scaled cone to select unseen targets behind terrain`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(100.0, 1.6, 0.0)
        val hiddenTarget = AABB(49.7, 0.0, 1.5, 50.3, 1.8, 2.1)
        val behindMountain = AABB(49.0, 0.0, 8.0, 49.6, 1.8, 8.6)

        assertNull(spearKillLookRayPriority(
            hiddenTarget,
            eye,
            lookEnd,
            hitboxMargin = spearKillTargetSelectionMargin(50.0, packetAStarEnabled = false),
        ))
        assertTrue(spearKillLookRayPriority(
            hiddenTarget,
            eye,
            lookEnd,
            hitboxMargin = spearKillTargetSelectionMargin(50.0, packetAStarEnabled = true),
        ) != null)
        assertNull(spearKillLookRayPriority(
            behindMountain,
            eye,
            lookEnd,
            hitboxMargin = spearKillTargetSelectionMargin(50.0, packetAStarEnabled = false),
        ))
        assertTrue(spearKillLookRayPriority(
            behindMountain,
            eye,
            lookEnd,
            hitboxMargin = spearKillTargetSelectionMargin(50.0, packetAStarEnabled = true),
        ) != null)
    }

    @Test
    fun `AStar through-terrain ranking prefers the aimed far target over a near interceptor`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(40.0, 1.6, 0.0)
        val nearerInterceptor = AABB(5.0, 0.0, -0.3, 5.6, 1.8, 0.3)
        val farTarget = AABB(30.0, 0.0, -0.25, 30.6, 1.8, 0.25)

        val nearPriority = spearKillLookRayPriority(nearerInterceptor, eye, lookEnd)!!
        val farPriority = spearKillLookRayPriority(farTarget, eye, lookEnd)!!

        assertTrue(nearPriority < farPriority)
        assertTrue(
            compareSpearKillLookRayPriority(
                left = farPriority,
                right = nearPriority,
                throughTerrain = true,
            ) < 0,
        )
    }

    @Test
    fun `AStar route failure rejects hard but a short damage window only retries`() {
        assertEquals(
            SpearKillAttackStartResult.REJECTED,
            classifySpearKillAStarStartFailure(routeFound = false, hasDamageWindow = true),
        )
        assertEquals(
            SpearKillAttackStartResult.RETRY_LATER,
            classifySpearKillAStarStartFailure(routeFound = true, hasDamageWindow = false),
        )
        assertEquals(
            SpearKillAttackStartResult.STARTED,
            classifySpearKillAStarStartFailure(routeFound = true, hasDamageWindow = true),
        )
    }

    @Test
    fun `target selection rejects vertical separation outside the widened margin`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(10.0, 1.6, 0.0)

        listOf(
            AABB(4.0, 20.0, -0.3, 4.6, 21.8, 0.3),
            AABB(4.0, -20.0, -0.3, 4.6, -18.2, 0.3),
        ).forEach { entityBox ->
            assertNull(spearKillLookRayPriority(entityBox, eye, lookEnd, hitboxMargin = 0.35))
        }
    }

    @Test
    fun `crosshair aligned target outranks a nearer target inside the look tolerance`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 1.6, 0.0)
        val nearerOffAxis = AABB(3.0, 0.0, 0.30, 3.6, 1.8, 0.90)
        val fartherAligned = AABB(8.0, 0.0, -0.3, 8.6, 1.8, 0.3)

        val offAxisPriority = spearKillLookRayPriority(nearerOffAxis, eye, lookEnd, hitboxMargin = 0.35)!!
        val alignedPriority = spearKillLookRayPriority(fartherAligned, eye, lookEnd, hitboxMargin = 0.35)!!

        assertFalse(offAxisPriority.directlyHovered)
        assertTrue(alignedPriority.directlyHovered)
        assertTrue(alignedPriority < offAxisPriority)
    }

    @Test
    fun `crosshair ray follows pitch and rejects a target at the wrong elevation`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 11.6, 0.0)
        val nearerSameYaw = AABB(3.0, 0.0, -0.3, 3.6, 1.8, 0.3)
        val hoveredHigherTarget = AABB(8.0, 5.3, -0.3, 8.6, 7.1, 0.3)

        val hoveredPriority = spearKillLookRayPriority(
            hoveredHigherTarget,
            eye,
            lookEnd,
            hitboxMargin = 0.35,
        )!!

        assertNull(spearKillLookRayPriority(nearerSameYaw, eye, lookEnd, hitboxMargin = 0.35))
        assertTrue(hoveredPriority.directlyHovered)
    }

    @Test
    fun `equally aligned targets use their first look ray hit as tie breaker`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 1.6, 0.0)
        val nearerAligned = AABB(3.0, 0.0, -0.3, 3.6, 1.8, 0.3)
        val fartherAligned = AABB(8.0, 0.0, -0.3, 8.6, 1.8, 0.3)

        val nearerPriority = spearKillLookRayPriority(nearerAligned, eye, lookEnd)!!
        val fartherPriority = spearKillLookRayPriority(fartherAligned, eye, lookEnd)!!

        assertTrue(nearerPriority < fartherPriority)
    }

    @Test
    fun `widened crosshair ray has a bounded vertical tolerance`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(20.0, 1.6, 0.0)
        val insideMargin = AABB(6.0, 1.90, -0.2, 6.6, 3.7, 0.2)
        val outsideMargin = AABB(6.0, 1.96, -0.2, 6.6, 3.8, 0.2)

        assertTrue(spearKillLookRayPriority(insideMargin, eye, lookEnd, hitboxMargin = 0.35) != null)
        assertNull(spearKillLookRayPriority(outsideMargin, eye, lookEnd, hitboxMargin = 0.35))
    }

    @Test
    fun `attack ray uses the aimed hitbox edge instead of its center`() {
        val eye = Vec3(0.0, 1.6, 0.2)
        val targetBox = AABB(4.0, 1.0, 0.0, 5.0, 2.0, 1.0)

        val hit = findSpearKillAttackHitPoint(
            eye = eye,
            direction = Vec3(1.0, 0.0, 0.0),
            targetBox = targetBox,
            range = 10.0,
        )!!

        assertVec3Equals(Vec3(4.0, 1.6, 0.2), hit, 1e-9)
        assertFalse(hit == targetBox.center)
    }

    @Test
    fun `attack ray follows elevation into the real target hitbox`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val targetBox = AABB(4.0, 5.0, -0.4, 5.0, 6.8, 0.4)

        val hit = findSpearKillAttackHitPoint(
            eye = eye,
            direction = targetBox.center.subtract(eye),
            targetBox = targetBox,
            range = 10.0,
        )!!

        assertTrue(hit.y >= targetBox.minY)
        assertTrue(hit.y <= targetBox.maxY)
    }

    @Test
    fun `AStar render path remains while its packet session is active after attack release`() {
        assertFalse(shouldClearSpearKillAStarRenderPath(
            attackKeyDown = false,
            packetSessionActive = true,
        ))
        assertTrue(shouldClearSpearKillAStarRenderPath(
            attackKeyDown = false,
            packetSessionActive = false,
        ))
        assertFalse(shouldClearSpearKillAStarRenderPath(
            attackKeyDown = true,
            packetSessionActive = false,
        ))
    }

    @Test
    fun `attack request accepts both a held key and a short recent click`() {
        assertTrue(isSpearKillAttackRequested(attackKeyDown = true, attackPressedRecently = false))
        assertTrue(isSpearKillAttackRequested(attackKeyDown = false, attackPressedRecently = true))
        assertFalse(isSpearKillAttackRequested(attackKeyDown = false, attackPressedRecently = false))
    }

    @Test
    fun `attack direction keeps a level target at eye height`() {
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = Vec3(0.0, 1.62, 0.0),
            predictedTargetPosition = Vec3(5.0, 0.0, 0.0),
            targetEyeOffset = Vec3(0.0, 1.62, 0.0),
            fallbackDirection = Vec3(0.0, 0.0, 1.0),
        )

        assertVec3Equals(Vec3(1.0, 0.0, 0.0), direction, 1e-9)
    }

    @Test
    fun `attack direction and round trip follow target elevation`() {
        val playerEyePosition = Vec3(0.0, 1.62, 0.0)
        val targetEyeOffset = Vec3(0.0, 1.62, 0.0)
        val fallbackDirection = Vec3(0.0, 0.0, 1.0)

        val higherTargetDirection = calculateSpearKillAttackDirection(
            playerEyePosition = playerEyePosition,
            predictedTargetPosition = Vec3(5.0, 4.0, 0.0),
            targetEyeOffset = targetEyeOffset,
            fallbackDirection = fallbackDirection,
        )
        val lowerTargetDirection = calculateSpearKillAttackDirection(
            playerEyePosition = playerEyePosition,
            predictedTargetPosition = Vec3(5.0, -4.0, 0.0),
            targetEyeOffset = targetEyeOffset,
            fallbackDirection = fallbackDirection,
        )

        assertVec3Equals(Vec3(5.0, 4.0, 0.0).normalize(), higherTargetDirection, 1e-9)
        assertVec3Equals(Vec3(5.0, -4.0, 0.0).normalize(), lowerTargetDirection, 1e-9)

        val higherMovements = buildSpearKillAttackMovements(higherTargetDirection, 10.0, 7.0)
        val lowerMovements = buildSpearKillAttackMovements(lowerTargetDirection, 10.0, 7.0)
        assertTrue(higherMovements.first().y > 0.0)
        assertTrue(lowerMovements.first().y < 0.0)
        assertTrue(higherMovements.dropLast(1).all { it.length() <= 7.0 })
        assertTrue(lowerMovements.dropLast(1).all { it.length() <= 7.0 })
        assertVec3Equals(Vec3.ZERO, higherMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertVec3Equals(Vec3.ZERO, lowerMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `attack direction supports a target directly above`() {
        val direction = calculateSpearKillAttackDirection(
            playerEyePosition = Vec3(4.0, 2.0, 7.0),
            predictedTargetPosition = Vec3(4.0, 12.0, 7.0),
            targetEyeOffset = Vec3.ZERO,
            fallbackDirection = Vec3(1.0, 0.0, 0.0),
        )

        assertVec3Equals(Vec3(0.0, 1.0, 0.0), direction, 1e-9)
    }

    @Test
    fun `fall protection starts only after a damaging fall begins`() {
        assertFalse(shouldProtectSpearKillFallDamage(
            fallDistance = 2.0,
            verticalVelocity = -1.0,
            safeFallDistance = 3.0,
            tickCount = 21,
        ))
        assertFalse(shouldProtectSpearKillFallDamage(
            fallDistance = 2.1,
            verticalVelocity = -1.0,
            safeFallDistance = 3.0,
            tickCount = 20,
        ))
        assertTrue(shouldProtectSpearKillFallDamage(
            fallDistance = 2.1,
            verticalVelocity = -1.0,
            safeFallDistance = 3.0,
            tickCount = 21,
        ))
    }

    @Test
    fun `fall protection confirms only its selected movement packet`() {
        val tracker = SpearKillFallDamagePacketTracker()
        val protectedPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)
        val unrelatedPacket = ServerboundMovePlayerPacket.StatusOnly(false, false)

        tracker.protect(protectedPacket)

        assertTrue(protectedPacket.onGround)
        assertFalse(tracker.confirmFinalState(unrelatedPacket, cancelled = false))
        assertTrue(tracker.confirmFinalState(protectedPacket, cancelled = false))
    }

    @Test
    fun `attack path uses bounded steps and returns to its origin`() {
        val movements = buildSpearKillAttackMovements(
            direction = Vec3(1.0, -1.0, 0.0).normalize(),
            distance = 16.0,
            maxSpeed = 7.0,
        )

        assertEquals(7, movements.size)
        assertTrue(movements.dropLast(1).all { it.length() <= 7.0 })
        assertTrue(movements.first().y < 0.0)
        assertVec3Equals(Vec3.ZERO, movements.fold(Vec3.ZERO, Vec3::add), 1e-9)
        assertVec3Equals(Vec3.ZERO, movements.last(), 1e-9)
    }

    @Test
    fun `direct attack long travel uses full fixed steps followed by its remainder`() {
        val movements = buildSpearKillAttackMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 10.0,
            maxSpeed = 3.0,
        )
        val expectedOutbound = listOf(
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(expectedOutbound + expectedOutbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO, movements)

        val shortMovements = buildSpearKillAttackMovements(
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 2.0,
            maxSpeed = 3.0,
        )
        assertEquals(3, shortMovements.size)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), shortMovements[0], 1e-9)
        assertVec3Equals(Vec3(-2.0, 0.0, 0.0), shortMovements[1], 1e-9)
        assertVec3Equals(Vec3.ZERO, shortMovements[2], 1e-9)
    }

    @Test
    fun `cancelled packet retries the same virtual step`() {
        val session = SpearKillPacketBootSession()
        session.start(listOf(Vec3(4.0, -2.0, 1.0), Vec3(-4.0, 2.0, -1.0), Vec3.ZERO))

        val firstAttempt = session.prepareNextStep()
        assertTrue(session.requiresDelivery)
        session.confirmStep(delivered = false)
        assertFalse(session.requiresDelivery)
        val retry = session.prepareNextStep()

        assertVec3Equals(Vec3(4.0, -2.0, 1.0), firstAttempt!!, 1e-9)
        assertVec3Equals(firstAttempt, retry!!, 1e-9)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `packet session commits exactly one step per confirmation`() {
        val session = SpearKillPacketBootSession()
        session.start(listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)))

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.committedOffset, 1e-9)
        assertVec3Equals(Vec3(6.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `Packet step wait inserts the configured idle ticks between delivered movements`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0), Vec3.ZERO),
            stepWaitTicks = 2,
        )

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertNull(session.prepareNextStep())
        assertVec3Equals(Vec3(6.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `cancelled Packet step retries immediately without consuming its configured wait`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(Vec3(3.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0), Vec3.ZERO),
            stepWaitTicks = 4,
        )

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `Packet target prediction includes inter-step wait time`() {
        assertEquals(4, spearKillPacketTravelTicks(stepCount = 4, stepWaitTicks = 0))
        assertEquals(10, spearKillPacketTravelTicks(stepCount = 4, stepWaitTicks = 2))
    }

    @Test
    fun `AStar prediction includes every shared Packet wait`() {
        assertEquals(4, spearKillAStarPredictionTicks(distance = 28.0, maxSpeed = 7.0, stepWaitTicks = 0))
        assertEquals(10, spearKillAStarPredictionTicks(distance = 28.0, maxSpeed = 7.0, stepWaitTicks = 2))
    }

    @Test
    fun `AStar arrival prediction uses the actual route and pre-strike barrier`() {
        assertEquals(10, spearKillAStarArrivalTicks(
            outboundStepCount = 8,
            stepWaitTicks = 0,
            preStrikeHoldTicks = 2,
        ))
        assertEquals(17, spearKillAStarArrivalTicks(
            outboundStepCount = 6,
            stepWaitTicks = 2,
            preStrikeHoldTicks = 1,
        ))
    }

    @Test
    fun `AStar waits for a fresh spear use window when its route would expire before impact`() {
        assertTrue(hasSpearKillDamageWindow(
            ticksUsingItem = 20,
            damageUseDuration = 80,
            arrivalTicks = 30,
            confirmationTicks = 2,
        ))
        assertFalse(hasSpearKillDamageWindow(
            ticksUsingItem = 60,
            damageUseDuration = 80,
            arrivalTicks = 20,
            confirmationTicks = 2,
        ))
    }

    @Test
    fun `AStar replaces only unconfirmed outbound tail and still returns exactly to origin`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(
                Vec3(2.0, 1.0, 0.0),
                Vec3(2.0, 1.0, 0.0),
                Vec3(-2.0, -1.0, 0.0),
                Vec3(-2.0, -1.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertTrue(session.canReplaceRemainingOutbound)
        assertTrue(session.replaceRemainingOutbound(
            outboundMovements = listOf(Vec3(0.0, 2.0, 2.0), Vec3(1.0, 0.0, 1.0)),
            strikeHoldTicks = 0,
        ))
        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertTrue(session.replaceRemainingOutbound(
            outboundMovements = listOf(Vec3(-1.0, 1.0, 2.0), Vec3(0.0, 1.0, 1.0)),
            strikeHoldTicks = 0,
        ))

        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `AStar refuses to replace a pending or returning packet path`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
        )

        session.prepareNextStep()
        assertFalse(session.canReplaceRemainingOutbound)
        assertFalse(session.replaceRemainingOutbound(listOf(Vec3(1.0, 0.0, 0.0)), 0))
        session.confirmStep(delivered = true)
        assertFalse(session.canReplaceRemainingOutbound)
        assertFalse(session.replaceRemainingOutbound(listOf(Vec3(1.0, 0.0, 0.0)), 0))
    }

    @Test
    fun `AStar strike hold waits after the final outbound step before returning`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            strikeHoldTicks = 2,
        )

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertTrue(session.active)
        assertTrue(session.holdingStrike)
        assertEquals(null, session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertEquals(null, session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        assertFalse(session.holdingStrike)
    }

    @Test
    fun `AStar isolates its terminal lunge behind a suppressed movement barrier`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                Vec3(-4.0, 0.0, 0.0),
                Vec3(7.0, 0.0, 0.0),
                Vec3(-7.0, 0.0, 0.0),
                Vec3(4.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            preStrikeHoldTicks = 2,
        )

        assertVec3Equals(Vec3(-4.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)
        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        assertFalse(session.holdingKineticBarrier)
    }

    @Test
    fun `AStar keeps its strike hold ahead of the configured step wait`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(Vec3(3.0, 0.0, 0.0), Vec3(-3.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 2,
            stepWaitTicks = 2,
        )

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertNull(session.prepareNextStep())
        assertTrue(session.holdingStrike)
        assertNull(session.prepareNextStep())
        assertFalse(session.holdingStrike)
        assertNull(session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `AStar strike hold suppresses only ambient movement packets`() {
        assertTrue(shouldSuppressSpearKillAStarStrikeHoldPacket(
            packetAStarAttackActive = true,
            holdingStrike = true,
        ))
        assertFalse(shouldSuppressSpearKillAStarStrikeHoldPacket(
            packetAStarAttackActive = false,
            holdingStrike = true,
        ))
        assertFalse(shouldSuppressSpearKillAStarStrikeHoldPacket(
            packetAStarAttackActive = true,
            holdingStrike = false,
        ))
    }

    @Test
    fun `cancelled final AStar outbound step retries before the strike hold`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 1,
        )

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertEquals(null, session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `AStar recovery clears a pending strike hold`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginRecovery(maxSpeed = 4.0)

        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `normal packet exposes only confirmed return positions`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(2.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertEquals(null, session.consumePhysicalPositionOffset())

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertTrue(session.recovering)
        assertVec3Equals(Vec3(4.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)

        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = false)
        assertEquals(null, session.consumePhysicalPositionOffset())
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)

        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertFalse(session.active)
    }

    @Test
    fun `aborted normal packet exposes its delivered endpoint and exact return`() {
        val session = SpearKillPacketBootSession()
        val first = Vec3(3.0, 0.0, 0.0)
        val second = Vec3(0.0, 0.0, 2.0)
        session.startPhysicalReturn(
            path = listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO),
            outboundSteps = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginExactReturn()

        assertTrue(session.recovering)
        assertVec3Equals(first, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertFalse(session.active)
    }

    @Test
    fun `AStar packet exposes its endpoint and confirmed return after the strike hold`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO),
            outboundSteps = 1,
            strikeHoldTicks = 1,
        )

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(2.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)

        assertEquals(null, session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)

        assertFalse(session.active)
    }

    @Test
    fun `normal setback recovery exposes authoritative offset and bounded return`() {
        val session = SpearKillPacketBootSession()

        session.beginPhysicalRecoveryFrom(
            authoritativeOffset = Vec3(9.0, 0.0, 0.0),
            maxSpeed = 4.0,
        )

        assertVec3Equals(Vec3(9.0, 0.0, 0.0), session.consumePhysicalPositionOffset()!!, 1e-9)
        while (session.active) {
            val before = session.committedOffset
            session.prepareNextStep()
            session.confirmStep(delivered = true)
            val physicalOffset = session.consumePhysicalPositionOffset()!!
            assertTrue(physicalOffset.subtract(before).length() <= 4.0)
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `return stays packet only when the client is still at the session origin`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(null, positioner.resolve(origin, origin, Vec3(8.0, 0.0, 0.0)))
        assertEquals(
            null,
            positioner.resolve(origin, origin, Vec3(4.0, 0.0, 0.0)),
        )
        assertEquals(null, positioner.resolve(origin, origin, Vec3.ZERO))
    }

    @Test
    fun `ordinary horizontal and vertical drift does not turn a packet return physical`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(
            null,
            positioner.resolve(origin, origin.add(1.4, 0.6, 0.8), Vec3(8.0, 4.0, 0.0)),
        )
        assertEquals(
            null,
            positioner.resolve(origin, origin.add(2.0, 0.0, 0.0), Vec3(4.0, 2.0, 0.0)),
        )
    }

    @Test
    fun `physical packet return begins only when truly far from its origin`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertVec3Equals(
            origin.add(4.0, 2.0, 0.0),
            positioner.resolve(origin, origin.add(2.01, 0.0, 0.0), Vec3(4.0, 2.0, 0.0))!!,
            1e-9,
        )
    }

    @Test
    fun `displaced client follows confirmed absolute return positions back to origin`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertVec3Equals(
            origin.add(8.0, 0.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 0.0, 0.0), Vec3(8.0, 0.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(
            origin.add(4.0, 0.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(origin, positioner.resolve(origin, origin.add(4.0, 0.0, 0.0), Vec3.ZERO)!!, 1e-9)
    }

    @Test
    fun `new return clears the previous packet only decision`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(null, positioner.resolve(origin, origin, Vec3(8.0, 0.0, 0.0)))
        positioner.clear()

        assertVec3Equals(
            origin.add(8.0, 0.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 0.0, 0.0), Vec3(8.0, 0.0, 0.0))!!,
            1e-9,
        )
    }

    @Test
    fun `late displacement upgrades a packet-only return to physical position updates`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(null, positioner.resolve(origin, origin, Vec3(8.0, 4.0, 0.0)))
        assertVec3Equals(
            origin.add(4.0, 2.0, 0.0),
            positioner.resolve(origin, origin.add(8.0, 4.0, 0.0), Vec3(4.0, 2.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(origin, positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3.ZERO)!!, 1e-9)
    }

    @Test
    fun `exact packet return reverses delivered steps once and reaches the origin`() {
        val session = SpearKillPacketBootSession()
        val first = Vec3(2.0, 0.0, 0.0)
        val second = Vec3(0.0, 0.0, 3.0)
        session.start(listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO))

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginExactReturn()

        assertTrue(session.recovering)
        assertVec3Equals(first, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        session.beginExactReturn()
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertFalse(session.active)
        assertFalse(session.recovering)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `trailing stop marker completes without requiring a duplicate position packet`() {
        val session = SpearKillPacketBootSession()
        session.start(listOf(Vec3(2.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO))

        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
        }

        assertFalse(session.active)
        assertEquals(null, session.prepareNextStep())
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `aborted packet path recovers with bounded steps`() {
        val session = SpearKillPacketBootSession()
        session.start(listOf(Vec3(10.0, -4.0, 0.0), Vec3(-10.0, 4.0, 0.0)))
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        session.beginRecovery(maxSpeed = 4.0)

        assertTrue(session.recovering)
        while (session.active) {
            val before = session.committedOffset
            val next = session.prepareNextStep()!!
            assertTrue(next.subtract(before).length() <= 4.0)
            session.confirmStep(delivered = true)
        }

        assertFalse(session.recovering)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `server setback replaces stale path and recovers with bounded steps`() {
        val session = SpearKillPacketBootSession()
        session.start(listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)))
        session.prepareNextStep()

        session.beginRecoveryFrom(Vec3(18.0, -4.0, 0.0), maxSpeed = 5.0)

        assertTrue(session.recovering)
        assertVec3Equals(Vec3(18.0, -4.0, 0.0), session.committedOffset, 1e-9)
        while (session.active) {
            val before = session.committedOffset
            val next = session.prepareNextStep()!!
            assertTrue(next.subtract(before).length() <= 5.0)
            session.confirmStep(delivered = true)
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `setback can reuse the exact collision-safe inverse of confirmed XYZ movement`() {
        val session = SpearKillPacketBootSession()
        val first = Vec3(5.0, 3.0, 0.0)
        val second = Vec3(0.0, 0.0, 4.0)
        session.startPhysicalReturn(
            path = listOf(first, second, second.scale(-1.0), first.scale(-1.0), Vec3.ZERO),
            outboundSteps = 2,
        )
        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
            session.consumePhysicalPositionOffset()
        }
        val authoritativeOffset = first.add(second)
        val exactReturn = session.exactRecoveryMovementsFrom(authoritativeOffset)!!

        assertEquals(listOf(second.scale(-1.0), first.scale(-1.0)), exactReturn)

        session.beginPhysicalExactRecoveryFrom(authoritativeOffset, exactReturn)
        val confirmedOffsets = mutableListOf(session.consumePhysicalPositionOffset()!!)
        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()?.let(confirmedOffsets::add)
        }

        assertEquals(listOf(authoritativeOffset, first, Vec3.ZERO), confirmedOffsets)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `late setback to a delivered virtual position restores the local state`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(
            Vec3(10.0, 64.0, 2.0),
            Vec3(0.2, -0.1, 0.3),
            45f,
            -20f,
        )
        guard.record(Vec3(18.0, 60.0, 2.0), localState.position)
        guard.tick(pathActive = false)

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                7,
                PositionMoveRotation(Vec3(18.0, 60.0, 2.0), Vec3.ZERO, 90f, 30f),
                emptySet(),
            ),
        )

        assertEquals(localState, restore)
    }

    @Test
    fun `relative setback coordinates match a delivered virtual position`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(Vec3(10.0, 64.0, 2.0), Vec3.ZERO, 0f, 0f)
        guard.record(Vec3(14.0, 64.0, 2.0), localState.position)

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                8,
                PositionMoveRotation(Vec3(4.0, 64.0, 2.0), Vec3.ZERO, 0f, 0f),
                setOf(Relative.X),
            ),
        )

        assertEquals(localState, restore)
    }

    @Test
    fun `only the marked correction packet completes a rollback`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val rollback = SpearKillSetbackRollback()
        val localState = SpearKillLocalPlayerState(
            movement = PositionMoveRotation(Vec3(10.0, 64.0, 2.0), Vec3.ZERO, 15f, -5f),
            oldPosition = Vec3(9.8, 64.0, 2.0),
            oldYRot = 14f,
            oldXRot = -4f,
        )
        val marked = ClientboundPlayerPositionPacket(
            10,
            PositionMoveRotation(Vec3(18.0, 60.0, 2.0), Vec3.ZERO, 90f, 30f),
            emptySet(),
        )
        val unrelated = ClientboundPlayerPositionPacket(
            11,
            marked.change,
            marked.relatives,
        )
        guard.record(marked.change.position, localState.movement.position)
        rollback.mark(marked)

        assertEquals(null, rollback.prepare(unrelated, localState, guard))
        val prepared = rollback.prepare(marked, localState, guard)
        assertVec3Equals(Vec3(8.0, -4.0, 0.0), prepared!!.authoritativeOffset, 1e-9)
        assertEquals(null, rollback.finish(unrelated))
        assertEquals(prepared, rollback.finish(marked))
        assertEquals(null, rollback.finish(marked))
    }

    @Test
    fun `expired setback guard leaves unrelated server teleports untouched`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(Vec3(10.0, 64.0, 2.0), Vec3.ZERO, 0f, 0f)
        guard.record(Vec3(14.0, 64.0, 2.0), localState.position)
        repeat(2) { guard.tick(pathActive = false) }

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                9,
                PositionMoveRotation(Vec3(14.0, 64.0, 2.0), Vec3.ZERO, 0f, 0f),
                emptySet(),
            ),
        )

        assertEquals(null, restore)
        assertFalse(guard.armed)
    }

    @Test
    fun `virtual position clears ground state when it changes height`() {
        val packet = ServerboundMovePlayerPacket.PosRot(
            10.0,
            20.0,
            30.0,
            45f,
            -20f,
            true,
            true,
        )
        applySpearKillVirtualPosition(packet, Vec3(10.0, 20.0, 30.0), Vec3(4.0, -8.0, 2.0))

        assertEquals(14.0, packet.x)
        assertEquals(12.0, packet.y)
        assertEquals(32.0, packet.z)
        assertEquals(45f, packet.yRot)
        assertEquals(-20f, packet.xRot)
        assertTrue(packet.hasPos)
        assertFalse(packet.isOnGround)
        assertTrue(packet.horizontalCollision())
    }

    @Test
    fun `virtual position keeps ground state during horizontal movement`() {
        val packet = ServerboundMovePlayerPacket.PosRot(
            10.0,
            20.0,
            30.0,
            45f,
            -20f,
            true,
            true,
        )

        applySpearKillVirtualPosition(packet, Vec3(10.0, 20.0, 30.0), Vec3(4.0, 0.0, 2.0))

        assertTrue(packet.isOnGround)
    }

    @Test
    fun `virtual movement applies a heading aligned with the kinetic motion`() {
        val movement = Vec3(2.0, -1.0, 3.0)
        val heading = spearKillKineticHeading(movement)!!
        val packet = ServerboundMovePlayerPacket.StatusOnly(false, false)

        applySpearKillVirtualPosition(
            packet = packet,
            playerPosition = Vec3(10.0, 20.0, 30.0),
            virtualOffset = movement,
            heading = heading,
        )

        assertVec3Equals(movement.normalize(), heading.directionVector, 1e-3)
        assertTrue(packet.hasRot)
        assertEquals(heading.yaw, packet.yRot)
        assertEquals(heading.pitch, packet.xRot)
        assertEquals(Rotation.fromRotationVec(movement), heading)
    }

    @Test
    fun `Packet path heading follows every XYZ step and overrides ambient camera rotation`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                Vec3(3.0, 0.0, 0.0),
                Vec3(0.0, 2.0, 3.0),
                Vec3(0.0, -2.0, -3.0),
                Vec3(-3.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            stepWaitTicks = 1,
        )

        session.prepareNextStep()
        val forwardHeading = session.pathHeading!!
        assertEquals(Rotation.fromRotationVec(Vec3(3.0, 0.0, 0.0)), forwardHeading)
        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        assertEquals(forwardHeading, session.pathHeading)

        session.prepareNextStep()
        assertEquals(Rotation.fromRotationVec(Vec3(0.0, 2.0, 3.0)), session.pathHeading)
        val ambientPacket = ServerboundMovePlayerPacket.Rot(140f, -70f, false, false)
        applySpearKillPathHeading(ambientPacket, session.pathHeading)
        assertEquals(session.pathHeading!!.yaw, ambientPacket.yRot)
        assertEquals(session.pathHeading!!.pitch, ambientPacket.xRot)
        assertTrue(ambientPacket.hasRot)

        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        session.prepareNextStep()
        assertEquals(Rotation.fromRotationVec(Vec3(0.0, -2.0, -3.0)), session.pathHeading)
    }

    @Test
    fun `terminal attack ray follows the serverbound kinetic movement`() {
        val eye = Vec3(0.0, 65.5, 0.0)
        val targetBox = AABB(4.0, 64.0, -0.5, 5.0, 66.0, 0.5)

        assertTrue(findSpearKillTerminalAttackHitPoint(
            eye = eye,
            terminalMovement = Vec3(7.0, 0.0, 0.0),
            targetBox = targetBox,
            range = 7.0,
        ) != null)
        assertNull(findSpearKillTerminalAttackHitPoint(
            eye = eye,
            terminalMovement = Vec3(0.0, 0.0, 7.0),
            targetBox = targetBox,
            range = 7.0,
        ))
    }
}

private fun JsonObject.previewSetting(choice: String, setting: String) = getAsJsonObject(choice)
    .getAsJsonArray("value")
    .map { it.asJsonObject }
    .single { it["name"].asString == setting }["value"]

private fun JsonObject.choiceValue(choice: String, value: String): JsonObject = getAsJsonObject(choice)
    .getAsJsonArray("value")
    .map { it.asJsonObject }
    .single { it["name"].asString == value }

private fun JsonObject.settingValue(setting: String) = getAsJsonArray("value")
    .map { it.asJsonObject }
    .single { it["name"].asString == setting }["value"]

private fun JsonObject.choiceValues(choice: String): List<String> = getAsJsonObject(choice)
    .getAsJsonArray("value")
    .map { it.asJsonObject["name"].asString }

private fun legacySpearKillMovementConfig(movement: String): JsonObject = JsonParser.parseString(
    """{ "name": "SpearKill", "value": [{ "name": "Movement", "value": "$movement" }] }""",
).asJsonObject

private fun JsonObject.spearKillMovement(): JsonObject = getAsJsonArray("value")
    .map { it.asJsonObject }
    .single { it["name"].asString == "Movement" }
