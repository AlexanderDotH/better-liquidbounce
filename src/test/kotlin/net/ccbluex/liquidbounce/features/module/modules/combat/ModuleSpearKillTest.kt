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

@Suppress("LargeClass")
class ModuleSpearKillTest {

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
    @Suppress("UNCHECKED_CAST", "LongMethod")
    fun `Movement nests AStar controls under the Routing choice`() {
        val configuration = SpearKillMovementConfiguration(null)
        val movement = configuration.choice

        assertEquals("Packet", movement.activeMode.name)
        assertEquals(listOf("TargetSpeed"), movement.inner.map { it.name })
        assertEquals(
            mapOf(
                "Motion" to listOf("StepDistance"),
                "Packet" to listOf("StepDistance", "StepDelay", "Routing"),
            ),
            movement.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
        val routing = configuration.packet.routing
        assertEquals("Direct", routing.activeMode.name)
        assertEquals(
            mapOf(
                "Direct" to emptyList(),
                "AStar" to listOf("MaxCost", "Diagonal", "LineOfSightShortcuts"),
            ),
            routing.modes.associate { it.name to it.inner.map { value -> value.name } },
        )
        val motionStepDistance = configuration.motion.inner.single {
            it.name == "StepDistance"
        } as RangedValue<Float>
        val packetStepDistance = configuration.packet.inner.single {
            it.name == "StepDistance"
        } as RangedValue<Float>
        val packetStepDelay = configuration.packet.inner.single {
            it.name == "StepDelay"
        } as RangedValue<Int>
        assertEquals(10f, motionStepDistance.get())
        assertEquals(17.32f, packetStepDistance.get())
        assertEquals(2f..500f, motionStepDistance.range)
        assertEquals(2f..500f, packetStepDistance.range)
        assertEquals(listOf("StepsPerTeleport", "StepLimit"), motionStepDistance.aliases)
        assertEquals(listOf("StepsPerTeleport", "StepLimit"), packetStepDistance.aliases)
        assertEquals(0, packetStepDelay.get())
        assertEquals(0..4, packetStepDelay.range)
        assertEquals(listOf("WaitBeforeTeleport", "WaitTicks"), packetStepDelay.aliases)

        val serializedMovement = fileGson.toJsonTree(movement, ModeValueGroup::class.java).asJsonObject
        val serializedRouting = serializedMovement.getAsJsonObject("choices")
            .choiceValue("Packet", "Routing")
        val serializedAStar = serializedRouting.getAsJsonObject("choices").getAsJsonObject("AStar")

        assertEquals(
            "Direct",
            serializedRouting["active"].asString,
        )
        assertEquals(setOf("Direct", "AStar"), serializedRouting.getAsJsonObject("choices").keySet())
        assertEquals(250, serializedAStar.settingValue("MaxCost").asInt)
        assertFalse(serializedAStar.settingValue("Diagonal").asBoolean)
        assertFalse(serializedAStar.settingValue("LineOfSightShortcuts").asBoolean)
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
        assertEquals(
            0,
            serializedMovement.getAsJsonObject("choices")
                .choiceValue("Packet", "StepDelay")["value"].asInt,
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

    @Test
    fun `aborting a displaced Packet session snaps back to the session origin`() {
        val origin = Vec3(10.0, 64.0, -3.0)
        assertEquals(
            origin,
            spearKillSessionAbortSnapPosition(
                sessionOrigin = origin,
                committedOffset = Vec3(2.0, 1.5, 0.0),
                physicalReturnConfigured = false,
            ),
        )
        assertEquals(
            origin,
            spearKillSessionAbortSnapPosition(
                sessionOrigin = origin,
                committedOffset = Vec3.ZERO,
                physicalReturnConfigured = true,
            ),
        )
        assertNull(
            spearKillSessionAbortSnapPosition(
                sessionOrigin = origin,
                committedOffset = Vec3.ZERO,
                physicalReturnConfigured = false,
            ),
        )
        assertNull(
            spearKillSessionAbortSnapPosition(
                sessionOrigin = null,
                committedOffset = Vec3(1.0, 0.0, 0.0),
                physicalReturnConfigured = true,
            ),
        )
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
        // Packet non-A* remains selectable on LOS; its route preflight owns body-corridor rejection.
        assertTrue(isSpearKillAStarTargetEligible(
            hasLineOfSight = true,
            hasClearDirectTravel = false,
            packetAStarEnabled = false,
            packetMovementMode = true,
        ))
        assertFalse(isSpearKillAStarTargetEligible(
            hasLineOfSight = false,
            hasClearDirectTravel = true,
            packetAStarEnabled = false,
            packetMovementMode = true,
        ))
    }

    @Test
    fun `dead Packet target is defeated before removal world and range failures`() {
        assertEquals(
            SpearKillPacketTargetState.DEFEATED,
            classifySpearKillPacketTargetState(
                isAlive = false,
                isRemoved = true,
                isInCurrentWorld = false,
                isWithinRange = false,
            ),
        )
    }

    @Test
    fun `alive invalid Packet targets are unreachable`() {
        listOf(
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = true,
                isInCurrentWorld = true,
                isWithinRange = true,
            ),
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = false,
                isInCurrentWorld = false,
                isWithinRange = true,
            ),
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = false,
                isInCurrentWorld = true,
                isWithinRange = false,
            ),
        ).forEach { state ->
            assertEquals(SpearKillPacketTargetState.UNREACHABLE, state)
        }

        assertEquals(
            SpearKillPacketTargetState.ACTIVE,
            classifySpearKillPacketTargetState(
                isAlive = true,
                isRemoved = false,
                isInCurrentWorld = true,
                isWithinRange = true,
            ),
        )
    }

    @Test
    fun `held undercharged spear waits for vanilla charge cadence without packet acceleration`() {
        assertEquals(
            SpearKillChargeDecision.WAIT_FOR_VANILLA,
            resolveSpearKillChargeDecision(
                ticksUsingItem = 2,
                delayTicks = 3,
                isUsingSpear = true,
                useRequested = true,
            ),
        )
    }

    @Test
    fun `interrupted undercharged spear releases SpearKill ownership`() {
        assertEquals(
            SpearKillChargeDecision.RESET,
            resolveSpearKillChargeDecision(
                ticksUsingItem = 2,
                delayTicks = 3,
                isUsingSpear = true,
                useRequested = false,
            ),
        )
    }

    @Test
    fun `charged spear continues to route planning`() {
        assertEquals(
            SpearKillChargeDecision.READY,
            resolveSpearKillChargeDecision(
                ticksUsingItem = 4,
                delayTicks = 3,
                isUsingSpear = true,
                useRequested = true,
            ),
        )
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
    fun `bidirectional AStar meets through a U-bend under a tight shared budget`() {
        // Cheap dead-end fan-out at S forces unidirectional search to exhaust the budget before the
        // corridor; bidirectional still meets from the goal side within the same shared budget.
        val deadEnds = (1..40).map { "X$it" }
        val corridor = listOf("S", "A", "B", "C", "D", "E", "F", "G")
        val edges = mutableMapOf<String, MutableList<WeightedEdge<String>>>()
        fun link(from: String, to: String, cost: Double) {
            edges.getOrPut(from) { mutableListOf() }.add(WeightedEdge(to, cost))
            edges.getOrPut(to) { mutableListOf() }.add(WeightedEdge(from, cost))
        }
        for (index in 0 until corridor.lastIndex) {
            link(corridor[index], corridor[index + 1], 1.0)
        }
        deadEnds.forEach { link("S", it, 0.5) }

        val zeroHeuristic = java.util.function.ToDoubleFunction<String> { 0.0 }
        val neighbors = { node: String -> edges[node].orEmpty() }
        val budget = 25

        assertNull(
            aStarShortestPath(
                start = "S",
                isGoal = { it == "G" },
                neighbors = neighbors,
                heuristic = zeroHeuristic,
                maxIterations = budget,
                maxCost = 100.0,
            ),
        )

        val path = bidirectionalAStarShortestPath(
            start = "S",
            end = "G",
            neighbors = neighbors,
            forwardHeuristic = zeroHeuristic,
            backwardHeuristic = zeroHeuristic,
            maxIterations = budget,
            maxCost = 100.0,
        )

        assertNotNull(path)
        assertEquals("S", path!!.nodes.first())
        assertEquals("G", path.nodes.last())
        assertTrue(path.nodes.none { it in deadEnds })
    }

    @Test
    fun `bidirectional AStar stops once the best meeting path is proven`() {
        var neighborCalls = 0
        val path = bidirectionalAStarShortestPath(
            start = 0,
            end = 10,
            neighbors = { node ->
                neighborCalls++
                buildList {
                    if (node > -1_000) add(WeightedEdge(node - 1, 1.0))
                    if (node < 1_000) add(WeightedEdge(node + 1, 1.0))
                }
            },
            forwardHeuristic = java.util.function.ToDoubleFunction { node -> kotlin.math.abs(10 - node).toDouble() },
            backwardHeuristic = java.util.function.ToDoubleFunction { node -> kotlin.math.abs(node).toDouble() },
            maxIterations = 500,
            maxCost = 100.0,
        )

        assertNotNull(path)
        assertEquals(10.0, path!!.totalCost, 1e-9)
        assertTrue(neighborCalls < 30, "Search kept expanding after proving the best path: $neighborCalls")
    }

    @Test
    fun `SpearKill AStar heuristic is admissible for long unit corridors`() {
        assertEquals(
            10.0,
            spearKillAStarHeuristic(Vec3i(0, 0, 0), Vec3i(10, 0, 0)),
            1e-9,
        )
        assertEquals(
            kotlin.math.sqrt(2.0),
            spearKillAStarHeuristic(Vec3i.ZERO, Vec3i(1, 0, 1)),
            1e-9,
        )
    }

    @Test
    fun `route planner caches passability across approach bearings`() {
        var passabilityChecks = 0
        val planner = SpearKillAStarRoutePlanner(
            allowDiagonal = false,
            maxCost = 250,
            isPassable = {
                passabilityChecks++
                true
            },
        )
        val origin = Vec3(0.5, 64.0, 0.5)
        val destination = Vec3(8.5, 64.0, 0.5)

        assertNotNull(planner.plan(origin, destination))
        val checksAfterFirstPlan = passabilityChecks
        assertNotNull(planner.plan(origin, destination))

        assertEquals(checksAfterFirstPlan, passabilityChecks)
    }

    @Test
    fun `route planner excludes server-rejected edges and finds a detour`() {
        val planner = SpearKillAStarRoutePlanner(
            allowDiagonal = false,
            maxCost = 250,
            isPassable = { position ->
                position.y == 64 && position.x in 0..3 && position.z in 0..1
            },
            canTraverse = { from, to ->
                val crossesRejectedEdge = from.z == 0.5 && to.z == 0.5 &&
                    setOf(from.x, to.x) == setOf(1.5, 2.5)
                !crossesRejectedEdge
            },
        )

        val route = planner.plan(
            origin = Vec3(0.5, 64.0, 0.5),
            destination = Vec3(3.5, 64.0, 0.5),
        )

        assertNotNull(route)
        assertTrue(route!!.any { it.z == 1.5 })
    }

    @Test
    fun `SpearKill AStar neighbors never jump through intermediate vertical blocks`() {
        val neighbors = spearKillBidirectionalNeighbors(
            position = Vec3i.ZERO,
            allowDiagonal = false,
            isPassable = { true },
        )

        assertEquals(6, neighbors.size)
        assertTrue(neighbors.all { edge ->
            val offset = edge.node.subtract(Vec3i.ZERO)
            kotlin.math.abs(offset.x) + kotlin.math.abs(offset.y) + kotlin.math.abs(offset.z) == 1
        })
    }

    @Test
    fun `AStar approach bearings are twelve evenly spaced directions with preferred first`() {
        val preferred = Vec3(1.0, 0.0, 0.0)
        val bearings = spearKillAStarLungeDirections(preferred)

        assertEquals(SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT, bearings.size)
        assertEquals(12, bearings.size)
        assertVec3Equals(preferred, bearings.first(), 1e-9)
        assertEquals(bearings[1].x, bearings[2].x, 1e-9)
        assertEquals(bearings[1].z, -bearings[2].z, 1e-9)
        assertEquals(0.0, bearings.last().distanceTo(preferred.scale(-1.0)), 1e-9)
        assertTrue(bearings.all { kotlin.math.abs(it.length() - 1.0) < 1e-9 && it.y == 0.0 })
    }

    @Test
    fun `clear direct run-up bypasses the block search`() {
        var routeSearches = 0
        val route = resolveSpearKillAStarApproachRoute(
            origin = Vec3.ZERO,
            plannerGoal = Vec3(20.0, 0.0, 0.0),
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            routeSearch = {
                routeSearches++
                null
            },
        )

        assertEquals(emptyList<Vec3>(), route)
        assertEquals(0, routeSearches)
    }

    @Test
    fun `swept segment validator follows the hitbox corridor instead of its full bounding rectangle`() {
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
        val offCorridorObstacle = AABB(0.0, 0.0, 9.0, 1.0, 2.0, 10.0)
        val onCorridorObstacle = AABB(4.5, 0.0, 4.5, 5.5, 2.0, 5.5)
        val from = Vec3.ZERO
        val to = Vec3(10.0, 0.0, 10.0)

        val offCorridorValidator = createSpearKillAStarSegmentValidator(
            origin = from,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(offCorridorObstacle))
            },
        )
        val onCorridorValidator = createSpearKillAStarSegmentValidator(
            origin = from,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(onCorridorObstacle))
            },
        )

        assertTrue(offCorridorValidator.isClear(from, to))
        assertFalse(onCorridorValidator.isClear(from, to))
    }

    @Test
    fun `long diagonal validation performs one cached hitbox raycast`() {
        var raycasts = 0
        var castMovement: Vec3? = null
        val validator = createSpearKillAStarSegmentValidator(
            origin = Vec3.ZERO,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasHitboxRaycastCollision = { _, movement ->
                raycasts++
                castMovement = movement
                false
            },
        )

        assertTrue(validator.isClear(Vec3.ZERO, Vec3(100.0, 0.0, 100.0)))
        assertTrue(validator.isClear(Vec3.ZERO, Vec3(100.0, 0.0, 100.0)))
        assertEquals(1, raycasts)
        assertVec3Equals(Vec3(100.0, 0.0, 100.0), castMovement!!, 1e-9)
    }

    @Test
    fun `vanilla shape scope ignores client walk-through solidification`() {
        val previous = ShapeFlag.noShapeChange
        ShapeFlag.noShapeChange = false
        try {
            val seen = withVanillaSpearKillBlockShapes {
                assertTrue(ShapeFlag.noShapeChange)
                "ok"
            }
            assertEquals("ok", seen)
            assertFalse(ShapeFlag.noShapeChange)
        } finally {
            ShapeFlag.noShapeChange = previous
        }
    }

    @Test
    fun `direct Packet hitbox raycast rejects a terrain lip across the route`() {
        val origin = Vec3.ZERO
        val destination = Vec3(4.0, 0.0, 0.0)
        val terrainLip = AABB(1.0, 0.0, -0.3, 1.5, 0.4, 0.3)

        val validator = createSpearKillDirectPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(terrainLip))
            },
        )

        assertFalse(validator.isClear(origin, destination))
    }

    @Test
    fun `server preflight rejects movement that would trigger moved wrongly`() {
        val requested = Vec3(17.32, 0.0, 0.0)

        assertTrue(
            isSpearKillServerPacketMovementAccepted(
                requestedMovement = requested,
                resolvedMovement = Vec3(17.1, 0.0, 0.0),
            ),
        )
        assertFalse(
            isSpearKillServerPacketMovementAccepted(
                requestedMovement = requested,
                resolvedMovement = Vec3(17.0, 0.0, 0.0),
            ),
        )
    }

    @Test
    fun `server packet validator rejects a terrain-clipped Elytra step`() {
        val origin = Vec3.ZERO
        val validator = createSpearKillServerPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasDestinationCollision = { false },
            resolveMovement = { _, movement -> movement.subtract(0.32, 0.0, 0.0) },
        )

        assertFalse(validator.isClear(origin, origin.add(17.32, 0.0, 0.0)))
    }

    @Test
    fun `direct Packet hitbox raycast rejects an occupied endpoint and wall`() {
        val origin = Vec3.ZERO
        val destination = Vec3(4.0, 0.0, 0.0)
        val playerBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
        val occupiedDestination = createSpearKillDirectPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(
                    box,
                    movement,
                    listOf(AABB(3.8, 0.0, -0.3, 4.5, 1.8, 0.3)),
                )
            },
        )
        val clippedByWall = createSpearKillDirectPacketSegmentValidator(
            origin = origin,
            playerBoundingBox = playerBox,
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(
                    box,
                    movement,
                    listOf(AABB(1.5, 0.0, -0.3, 2.0, 1.8, 0.3)),
                )
            },
        )

        assertFalse(occupiedDestination.isClear(origin, destination))
        assertFalse(clippedByWall.isClear(origin, destination))
    }

    @Test
    fun `AStar hitbox raycast rejects an elevated diagonal obstacle`() {
        val from = Vec3.ZERO
        val to = Vec3(2.0, 1.0, 0.0)
        val obstacle = AABB(0.9, 2.0, -0.2, 1.3, 2.5, 0.2)
        val validator = createSpearKillAStarSegmentValidator(
            origin = from,
            playerBoundingBox = AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            hasHitboxRaycastCollision = { box, movement ->
                hasSpearKillHitboxRaycastCollision(box, movement, listOf(obstacle))
            },
        )

        assertFalse(validator.isClear(from, to))
    }

    @Test
    fun `AStar neighbors always consult canTraverse for passable edges`() {
        val origin = BlockPos(0, 64, 0)
        val openCells = setOf(
            origin,
            origin.offset(1, 0, 0),
            origin.offset(0, 1, 0),
            origin.offset(0, 0, 1),
            origin.offset(1, 0, 1),
        )
        val blocked = origin.offset(1, 0, 0)
        val neighbors = spearKillBidirectionalNeighbors(
            position = origin,
            allowDiagonal = true,
            isPassable = { it in openCells },
            canTraverse = { _, to -> to != blocked },
        )

        assertFalse(neighbors.any { it.node == blocked })
        assertTrue(neighbors.any { it.node == origin.offset(0, 1, 0) })
        assertTrue(neighbors.any { it.node == origin.offset(1, 0, 1) })
    }

    @Test
    fun `line of sight shortcuts pull non-collinear clear corridors`() {
        val origin = Vec3(0.0, 64.0, 0.0)
        val waypoints = listOf(
            Vec3(1.0, 64.0, 0.0),
            Vec3(2.0, 64.0, 1.0),
            Vec3(3.0, 64.0, 1.0),
            Vec3(4.0, 64.0, 0.0),
        )
        val alwaysClear = SpearKillAStarSegmentValidator { _, _ -> true }
        val collinear = simplifySpearKillAStarWaypoints(origin, waypoints, maxSpeed = 2.0, alwaysClear)
        val los = simplifySpearKillAStarWaypointsWithLineOfSight(origin, waypoints, alwaysClear)

        assertTrue(los.size < collinear.size)
        // LOS may jump farther than StepLimit; packet expansion splits afterward.
        assertEquals(listOf(Vec3(4.0, 64.0, 0.0)), los)

        val blockedFar = SpearKillAStarSegmentValidator { from, to ->
            from.distanceTo(to) <= 1.5
        }
        val blockedLos = simplifySpearKillAStarWaypointsWithLineOfSight(origin, waypoints, blockedFar)
        assertEquals(waypoints, blockedLos)
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
            maxSpeed = resolveSpearKillMovementTransport(7.0, 17.32, elytraActive = false).maxSpeed,
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
    fun `AStar accepts a terminal lunge split into StepLimit packets plus remainder`() {
        val approach = SpearKillAStarAttackApproach(
            plannerGoal = Vec3(0.0, 64.0, 0.0),
            terminalWaypoint = Vec3(7.0, 64.0, 0.0),
        )

        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(7.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(4.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 4.0,
        ))
        assertTrue(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(3.0, 0.0, 0.0), Vec3(3.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 3.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(-5.0, 0.0, 0.0), Vec3(0.0, 0.0, 7.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
        assertFalse(isSpearKillAStarTerminalStepValid(
            outboundMovements = listOf(Vec3(8.0, 0.0, 0.0)),
            approach = approach,
            stepLimit = 7.0,
        ))
    }

    @Test
    fun `AStar terminal lunge length follows MaxSpeed while StepLimit only chunks packets`() {
        val hitPoint = Vec3(10.0, 65.5, 0.0)
        val eyeOffset = Vec3(0.0, 1.5, 0.0)
        val approach = createSpearKillAStarAttackApproach(
            targetHitPoint = hitPoint,
            playerEyeOffset = eyeOffset,
            lookDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = 10.0,
        )!!
        val route = buildSpearKillAStarPacketRoute(
            origin = approach.plannerGoal,
            outboundWaypoints = listOf(approach.terminalWaypoint),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(10.0, approach.plannerGoal.distanceTo(approach.terminalWaypoint), 1e-9)
        assertEquals(
            listOf(
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
            ),
            route.outboundMovements,
        )
        assertTrue(isSpearKillAStarTerminalStepValid(route.outboundMovements, approach, stepLimit = 3.0))
        assertVec3Equals(
            approach.terminalWaypoint.subtract(approach.plannerGoal),
            route.outboundMovements.fold(Vec3.ZERO, Vec3::add),
            1e-9,
        )
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

        assertEquals(SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT, approaches.size)
        assertTrue(approaches.all { approach ->
            approach.terminalWaypoint.subtract(approach.plannerGoal).y == 0.0
        })
    }

    @Test
    fun `AStar approach candidates preserve MaxSpeed terminal length on every bearing`() {
        val maxSpeed = 10.0
        val approaches = createSpearKillAStarAttackApproachCandidates(
            targetBox = AABB(10.0, 64.0, 0.0, 11.0, 66.0, 1.0),
            targetEyePosition = Vec3(10.5, 65.5, 0.5),
            playerEyeOffset = Vec3(0.0, 1.62, 0.0),
            preferredDirection = Vec3(1.0, 0.0, 0.0),
            terminalLungeDistance = maxSpeed,
        )

        assertEquals(SPEAR_KILL_A_STAR_APPROACH_BEARING_COUNT, approaches.size)
        assertTrue(approaches.all { approach ->
            kotlin.math.abs(
                approach.terminalWaypoint.subtract(approach.plannerGoal).length() - maxSpeed,
            ) < 1e-9
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
    fun `packet route validates outbound and exact-inverse return corridors`() {
        var validationCalls = 0
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(2.0, 0.0, 0.0), Vec3(5.0, 0.0, 0.0)),
            maxSpeed = 3.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ ->
                validationCalls++
                true
            },
        )

        assertNotNull(route)
        // Two outbound packet edges plus their exact inverse returns. Reverse is not free under
        // server-faithful collision (sand lips / stairs can clip one direction only).
        assertEquals(4, validationCalls)
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
    fun `experimental Packet route supports one five hundred block step and its exact inverse`() {
        val route = buildSpearKillAStarPacketRoute(
            origin = Vec3.ZERO,
            outboundWaypoints = listOf(Vec3(500.0, 0.0, 0.0)),
            maxSpeed = 500.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!

        assertEquals(listOf(Vec3(500.0, 0.0, 0.0)), route.outboundMovements)
        assertEquals(3, route.roundTripMovements.size)
        assertVec3Equals(Vec3(500.0, 0.0, 0.0), route.roundTripMovements[0], 1e-9)
        assertVec3Equals(Vec3(-500.0, 0.0, 0.0), route.roundTripMovements[1], 1e-9)
        assertVec3Equals(Vec3.ZERO, route.roundTripMovements[2], 1e-9)
    }

    @Test
    fun `AStar follow replans moved targets with distance and tick hysteresis`() {
        val planned = Vec3(10.0, 64.0, 4.0)

        assertFalse(shouldReplanSpearKillAStarTarget(planned, planned.add(0.1, 0.0, 0.0), 20))
        assertFalse(shouldReplanSpearKillAStarTarget(planned, planned.add(1.0, 0.0, 0.0), 2))
        assertFalse(shouldReplanSpearKillAStarTarget(planned, planned.add(0.49, 0.0, 0.0), 3))
        assertTrue(shouldReplanSpearKillAStarTarget(planned, planned.add(1.0, 0.0, 0.0), 3))
        assertTrue(shouldReplanSpearKillAStarTarget(planned, planned.add(0.0, 1.0, 0.0), 3))
    }

    @Test
    fun `AStar follow does not replan steady target motion already predicted by the route`() {
        val planned = Vec3(10.0, 64.0, 4.0)
        val velocity = Vec3(0.2, 0.0, 0.0)

        assertFalse(shouldReplanSpearKillAStarTarget(
            plannedPosition = planned,
            currentPosition = planned.add(0.6, 0.0, 0.0),
            ticksSincePlan = 3,
            plannedVelocity = velocity,
        ))
        assertTrue(shouldReplanSpearKillAStarTarget(
            plannedPosition = planned,
            currentPosition = planned.add(0.6, 0.0, 1.0),
            ticksSincePlan = 3,
            plannedVelocity = velocity,
        ))
    }

    @Test
    fun `clear direct Packet corridor uses bounded outbound and exact inverse return`() {
        val route = buildSpearKillDirectPacketRoute(
            origin = Vec3(4.0, 64.0, -2.0),
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 25.0,
            maxSpeed = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
        )!!
        val expectedOutbound = listOf(
            Vec3(10.0, 0.0, 0.0),
            Vec3(10.0, 0.0, 0.0),
            Vec3(5.0, 0.0, 0.0),
        )

        assertEquals(expectedOutbound, route.outboundMovements)
        assertEquals(
            expectedOutbound + expectedOutbound.asReversed().map { it.scale(-1.0) } + Vec3.ZERO,
            route.roundTripMovements,
        )
    }

    @Test
    fun `direct Packet preflight rejects a later blocked outbound edge`() {
        val origin = Vec3(4.0, 64.0, -2.0)
        val validatedEdges = mutableListOf<Pair<Vec3, Vec3>>()

        val route = buildSpearKillDirectPacketRoute(
            origin = origin,
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 25.0,
            maxSpeed = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to ->
                validatedEdges += from to to
                from.x < origin.x + 10.0
            },
        )

        assertNull(route)
        assertEquals(
            listOf(
                origin to origin.add(10.0, 0.0, 0.0),
                origin.add(10.0, 0.0, 0.0) to origin.add(20.0, 0.0, 0.0),
            ),
            validatedEdges,
        )
    }

    @Test
    fun `direct Packet preflight rejects an inverse-only collision`() {
        val origin = Vec3(4.0, 64.0, -2.0)

        assertNull(
            buildSpearKillDirectPacketRoute(
                origin = origin,
                direction = Vec3(1.0, 0.0, 0.0),
                distance = 25.0,
                maxSpeed = 10.0,
                segmentValidator = SpearKillAStarSegmentValidator { from, to -> to.x >= from.x },
            ),
        )
    }

    @Test
    fun `blocked direct Packet route produces a blocked start result`() {
        val route = buildSpearKillDirectPacketRoute(
            origin = Vec3.ZERO,
            direction = Vec3(1.0, 0.0, 0.0),
            distance = 10.0,
            maxSpeed = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
        )
        val startResult = if (route == null) {
            SpearKillAttackStartResult.BLOCKED
        } else {
            SpearKillAttackStartResult.STARTED
        }

        assertEquals(SpearKillAttackStartResult.BLOCKED, startResult)
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
    fun `AStar packet route bounds every vertical delta below fall damage distance`() {
        val origin = Vec3(0.5, 72.0, 0.5)
        val destination = Vec3(0.5, 62.0, 0.5)
        val route = buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(destination),
            maxSpeed = 7.4,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> true },
            maxVerticalStep = 2.95,
        )!!

        assertTrue(route.roundTripMovements.dropLast(1).all { abs(it.y) <= 2.95 })
        assertVec3Equals(destination, route.outboundMovements.fold(origin, Vec3::add), 1e-9)
        assertVec3Equals(Vec3.ZERO, route.roundTripMovements.fold(Vec3.ZERO, Vec3::add), 1e-9)
    }

    @Test
    fun `AStar packet route rejects a server-blocked inverse return edge`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val waypoint = Vec3(1.5, 64.0, 0.75)

        assertNull(buildSpearKillAStarPacketRoute(
            origin = origin,
            outboundWaypoints = listOf(waypoint),
            maxSpeed = 2.0,
            segmentValidator = SpearKillAStarSegmentValidator { from, to -> to.x >= from.x },
        ))
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
    fun `target selection uses a fixed hitbox pad not a distance-scaled cone`() {
        val eye = Vec3(0.0, 1.6, 0.0)
        val lookEnd = Vec3(50.0, 1.6, 0.0)
        val margin = spearKillTargetSelectionMargin()
        // Slightly outside the vanilla box, still inside the fixed pad.
        val insidePad = AABB(20.0, 0.0, 0.40, 20.6, 1.8, 1.00)
        val outsidePad = AABB(20.0, 0.0, 0.90, 20.6, 1.8, 1.50)

        assertEquals(0.75, margin, 1e-9)
        assertEquals(margin, spearKillTargetSelectionMargin())
        assertTrue(spearKillLookRayPriority(insidePad, eye, lookEnd, hitboxMargin = margin) != null)
        assertNull(spearKillLookRayPriority(outsidePad, eye, lookEnd, hitboxMargin = margin))
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
    fun `direct Packet prediction includes the terminal strike hold`() {
        assertEquals(2, spearKillDirectPacketHitTicks(stepCount = 1, stepWaitTicks = 0))
        assertEquals(11, spearKillDirectPacketHitTicks(stepCount = 4, stepWaitTicks = 2))
    }

    @Test
    fun `direct Packet start waits for a damage window that reaches the server hit tick`() {
        assertTrue(hasSpearKillDirectPacketDamageWindow(
            ticksUsingItem = 8,
            damageUseDuration = 10,
            stepCount = 1,
            stepWaitTicks = 0,
        ))
        assertFalse(hasSpearKillDirectPacketDamageWindow(
            ticksUsingItem = 9,
            damageUseDuration = 10,
            stepCount = 1,
            stepWaitTicks = 0,
        ))
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
    fun `path schedule rejects waits beyond one aim-lock tick`() {
        assertNull(buildSpearKillPathSchedule(
            outboundStepCount = 6,
            stepWaitTicks = 1,
            terminalSuffixCount = 3,
            preStrikeHoldTicks = 2,
            strikeHoldTicks = 2,
        ))
    }

    @Test
    fun `candidate lower bound includes approach terminal packets waits and strike hold`() {
        assertEquals(
            7,
            spearKillAStarCandidateLowerBoundHitTick(
                routeOrigin = Vec3.ZERO,
                plannerGoal = Vec3(20.0, 0.0, 0.0),
                stepLimit = 10.0,
                terminalLungeDistance = 10.0,
                stepWaitTicks = 1,
                strikeHoldTicks = 2,
            ),
        )
    }

    @Test
    fun `approach refinement reacts only to horizontal seed drift over half a block`() {
        val seed = Vec3(5.0, 64.0, 5.0)

        assertFalse(shouldRefineSpearKillAStarApproach(seed, seed.add(0.5, 10.0, 0.0)))
        assertTrue(shouldRefineSpearKillAStarApproach(seed, seed.add(0.51, 0.0, 0.0)))
    }

    @Test
    fun `AStar schedule uses one aim-lock tick without predictive waiting`() {
        val schedule = buildSpearKillAStarPathSchedule(
            outboundStepCount = 3,
            stepWaitTicks = 0,
            terminalSuffixCount = 1,
            strikeHoldTicks = 2,
        )!!

        assertEquals(listOf(0, 1, 3), schedule.stepStartTicks)
        assertEquals(3, schedule.terminalStartTick)
        assertEquals(5, schedule.hitTick)
    }

    @Test
    fun `terminal suffix count matches trailing MaxSpeed corridor packets`() {
        val approach = SpearKillAStarAttackApproach(
            plannerGoal = Vec3(0.0, 64.0, 0.0),
            terminalWaypoint = Vec3(10.0, 64.0, 0.0),
        )
        val outbound = listOf(
            Vec3(2.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
        )

        assertEquals(4, countSpearKillAStarTerminalSuffix(outbound, approach, stepLimit = 3.0))
        assertNull(countSpearKillAStarTerminalSuffix(outbound.dropLast(1), approach, stepLimit = 3.0))
    }

    @Test
    fun `schedule damage window gates on hitTick`() {
        assertTrue(hasSpearKillScheduleDamageWindow(
            ticksUsingItem = 10,
            damageUseDuration = 40,
            hitTick = 30,
        ))
        assertFalse(hasSpearKillScheduleDamageWindow(
            ticksUsingItem = 20,
            damageUseDuration = 40,
            hitTick = 21,
        ))
        assertTrue(hasSpearKillAStarDamageWindow(
            ticksUsingItem = 10,
            damageUseDuration = 40,
            outboundStepCount = 4,
            stepWaitTicks = 0,
            confirmationTicks = 2,
            preStrikeHoldTicks = 0,
            terminalSuffixCount = 1,
        ))
    }

    @Test
    fun `terminal commit requires live aim charge and remaining damage window`() {
        assertTrue(canCommitSpearKillTerminalLunge(
            isUsingSpear = true,
            ticksUsingItem = 8,
            delayTicks = 5,
            damageUseDuration = 20,
            remainingHitTicks = 4,
            hasLiveAttackRay = true,
            aimAligned = true,
        ))
        assertFalse(canCommitSpearKillTerminalLunge(
            isUsingSpear = true,
            ticksUsingItem = 5,
            delayTicks = 5,
            damageUseDuration = 20,
            remainingHitTicks = 4,
            hasLiveAttackRay = true,
            aimAligned = true,
        ))
        assertFalse(canCommitSpearKillTerminalLunge(
            isUsingSpear = true,
            ticksUsingItem = 18,
            delayTicks = 5,
            damageUseDuration = 20,
            remainingHitTicks = 4,
            hasLiveAttackRay = true,
            aimAligned = true,
        ))
        assertFalse(canCommitSpearKillTerminalLunge(
            isUsingSpear = true,
            ticksUsingItem = 8,
            delayTicks = 5,
            damageUseDuration = 20,
            remainingHitTicks = 4,
            hasLiveAttackRay = false,
            aimAligned = true,
        ))
    }

    @Test
    fun `terminal commit aim must point at the predicted target center`() {
        val eye = Vec3(0.0, 65.5, 0.0)
        val movement = Vec3(7.0, 0.0, 0.0)

        assertTrue(isSpearKillTerminalAimAligned(
            eye = eye,
            terminalMovement = movement,
            targetPoint = Vec3(2.5, 65.5, 0.0),
        ))
        assertFalse(isSpearKillTerminalAimAligned(
            eye = eye,
            terminalMovement = movement,
            targetPoint = Vec3(2.5, 65.5, 0.2),
        ))
    }

    @Test
    fun `timed plan selection prefers earlier hits then fewer outbound steps`() {
        assertTrue(isBetterSpearKillTimedAStarPlan(
            candidateHitTick = 10,
            candidateOutboundSteps = 8,
            bestHitTick = 12,
            bestOutboundSteps = 4,
        ))
        assertFalse(isBetterSpearKillTimedAStarPlan(
            candidateHitTick = 12,
            candidateOutboundSteps = 3,
            bestHitTick = 10,
            bestOutboundSteps = 8,
        ))
        assertTrue(isBetterSpearKillTimedAStarPlan(
            candidateHitTick = 10,
            candidateOutboundSteps = 3,
            bestHitTick = 10,
            bestOutboundSteps = 5,
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
    fun `defeated Packet target can chain from its endpoint and still return to the first origin`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(-3.0, 0.0, 0.0),
                Vec3(-2.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            strikeHoldTicks = 2,
        )
        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
        }

        assertTrue(session.canStartChainedOutbound)
        assertTrue(session.startChainedOutbound(
            outboundMovements = listOf(
                Vec3(0.0, 4.0, 0.0),
                Vec3(0.0, 1.0, 0.0),
            ),
            strikeHoldTicks = 0,
        ))
        assertFalse(session.recovering)

        repeat(2) {
            session.prepareNextStep()
            session.confirmStep(delivered = true)
        }
        assertTrue(session.canStartChainedOutbound)
        assertTrue(session.startChainedOutbound(
            outboundMovements = listOf(Vec3(0.0, 0.0, 3.0)),
            strikeHoldTicks = 0,
        ))

        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `Packet origin fallback closes chaining before its first return packet`() {
        val outbound = Vec3(5.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(outbound, outbound.scale(-1.0), Vec3.ZERO),
            outboundSteps = 1,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertTrue(session.canStartChainedOutbound)
        session.beginExactReturn()
        assertFalse(session.canStartChainedOutbound)

        while (session.active) {
            session.prepareNextStep()?.let { session.confirmStep(delivered = true) }
            session.consumePhysicalPositionOffset()
        }
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
    }

    @Test
    fun `Motion chain prepends a round trip and preserves its existing origin return tail`() {
        val outbound = listOf(Vec3(0.0, 3.0, 0.0), Vec3(0.0, 2.0, 0.0))
        val existingReturn = listOf(Vec3(-3.0, 0.0, 0.0), Vec3(-2.0, 0.0, 0.0), Vec3.ZERO)

        val expected = listOf(
            Vec3(0.0, 3.0, 0.0),
            Vec3(0.0, 2.0, 0.0),
            Vec3(0.0, -2.0, 0.0),
            Vec3(0.0, -3.0, 0.0),
            Vec3(-3.0, 0.0, 0.0),
            Vec3(-2.0, 0.0, 0.0),
            Vec3.ZERO,
        )
        val actual = buildSpearKillChainedAttackMovements(outbound, existingReturn)

        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedMovement, actualMovement) ->
            assertVec3Equals(expectedMovement, actualMovement, 1e-9)
        }
    }

    @Test
    fun `selected round trip is rejected before emission when one server step clips`() {
        val origin = Vec3(10.0, 64.0, 2.0)
        val route = SpearKillAStarPacketRoute(
            outboundMovements = listOf(Vec3(17.32, 0.0, 0.0)),
            roundTripMovements = listOf(
                Vec3(17.32, 0.0, 0.0),
                Vec3(-17.32, 0.0, 0.0),
                Vec3.ZERO,
            ),
        )
        val validator = SpearKillAStarSegmentValidator { from, to ->
            to.subtract(from).x <= 17.0
        }

        assertFalse(isSpearKillPacketRouteServerAccepted(origin, route, validator))
    }

    @Test
    fun `unsafe pending outbound step returns along only confirmed movement`() {
        val firstMovement = Vec3(2.0, 1.0, 0.0)
        val unsafeMovement = Vec3(4.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(
                firstMovement,
                unsafeMovement,
                unsafeMovement.scale(-1.0),
                firstMovement.scale(-1.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )

        assertVec3Equals(firstMovement, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        val unsafeOffset = firstMovement.add(unsafeMovement)
        assertVec3Equals(unsafeOffset, session.prepareNextStep()!!, 1e-9)
        assertFalse(isSpearKillPacketStepClear(
            sessionOrigin = Vec3.ZERO,
            committedOffset = firstMovement,
            candidateOffset = unsafeOffset,
            maxStepLength = 10.0,
            segmentValidator = SpearKillAStarSegmentValidator { _, _ -> false },
        ))

        session.confirmStep(delivered = false)
        session.beginExactReturn()

        assertTrue(session.recovering)
        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        assertVec3Equals(firstMovement.scale(-1.0), session.pendingMovement!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1e-9)
        assertVec3Equals(Vec3.ZERO, session.consumePhysicalPositionOffset()!!, 1e-9)
        assertFalse(session.active)
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
    fun `direct Packet preserves terminal motion through its strike hold before exact return`() {
        val outbound = Vec3(6.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()
        val route = SpearKillAStarPacketRoute(
            outboundMovements = listOf(outbound),
            roundTripMovements = listOf(outbound, outbound.scale(-1.0), Vec3.ZERO),
        )

        startSpearKillDirectPacketSession(
            session = session,
            route = route,
            stepWaitTicks = 0,
        )
        assertVec3Equals(outbound, session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        repeat(SPEAR_KILL_PACKET_STRIKE_HOLD_TICKS) {
            assertNull(session.prepareNextStep())
            assertTrue(session.holdingStrike)
            assertEquals(spearKillKineticHeading(outbound), session.pathHeading)
        }

        assertVec3Equals(Vec3.ZERO, session.prepareNextStep()!!, 1e-9)
        assertVec3Equals(outbound.scale(-1.0), session.pendingMovement!!, 1e-9)
        assertFalse(session.holdingStrike)
    }

    @Test
    fun `AStar rejects waits longer than its one aim-lock tick`() {
        val terminal = Vec3(3.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()

        assertThrows<IllegalArgumentException> {
            session.start(
                path = listOf(terminal, terminal.scale(-1.0), Vec3.ZERO),
                outboundSteps = 1,
                preStrikeHoldTicks = 2,
                terminalSuffixSteps = 1,
                requireTerminalAuthorization = true,
            )
        }
    }

    @Test
    fun `AStar isolates its terminal lunge behind a one-tick movement barrier`() {
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
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 1,
        )

        assertVec3Equals(Vec3(-4.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)
        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        assertFalse(session.holdingKineticBarrier)
    }

    @Test
    fun `AStar keeps one aim-lock tick before the multi-step terminal suffix`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3(-3.0, 0.0, 0.0),
                Vec3(-3.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 4,
            stepWaitTicks = 0,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 3,
        )

        assertVec3Equals(Vec3(1.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)

        assertVec3Equals(Vec3(4.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(7.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(8.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `AStar terminal lunge cannot move before aim lock and live authorization`() {
        val terminalMovement = Vec3(7.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(terminalMovement, terminalMovement.scale(-1.0), Vec3.ZERO),
            outboundSteps = 1,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 1,
            requireTerminalAuthorization = true,
        )

        assertTrue(session.awaitingTerminalCommitAuthorization)
        assertFalse(session.terminalAimLockComplete)
        assertFalse(session.authorizeTerminalCommit())

        assertNull(session.prepareNextStep())
        assertTrue(session.holdingPreStrike)
        assertTrue(session.terminalAimLockComplete)

        assertNull(session.prepareNextStep())
        assertTrue(session.awaitingTerminalCommitAuthorization)
        assertTrue(session.authorizeTerminalCommit())
        assertVec3Equals(terminalMovement, session.prepareNextStep()!!, 1e-9)
    }

    @Test
    fun `AStar replanning freezes before terminal suffix begins`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 4,
            terminalSuffixSteps = 3,
        )

        assertTrue(session.canReplaceRemainingApproach)
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertFalse(session.canReplaceRemainingApproach)
    }

    @Test
    fun `AStar replanning waits until the current packet cadence is ready`() {
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 3,
            stepWaitTicks = 2,
            terminalSuffixSteps = 1,
        )

        session.prepareNextStep()
        session.confirmStep(delivered = true)
        assertFalse(session.canReplaceRemainingApproach)
        assertNull(session.prepareNextStep())
        assertFalse(session.canReplaceRemainingApproach)
        assertNull(session.prepareNextStep())
        assertTrue(session.canReplaceRemainingApproach)
    }

    @Test
    fun `AStar replan applies pre-hold and terminal suffix count to the replacement outbound`() {
        val session = SpearKillPacketBootSession()
        session.startPhysicalReturn(
            path = listOf(
                Vec3(1.0, 0.0, 0.0),
                Vec3(1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3(-1.0, 0.0, 0.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
        )
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertTrue(session.replaceRemainingOutbound(
            outboundMovements = listOf(
                Vec3(2.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
                Vec3(3.0, 0.0, 0.0),
            ),
            strikeHoldTicks = 2,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 2,
        ))

        assertVec3Equals(Vec3(3.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        assertTrue(session.holdingKineticBarrier)
        assertVec3Equals(Vec3(6.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
        session.confirmStep(delivered = true)
        assertVec3Equals(Vec3(9.0, 0.0, 0.0), session.prepareNextStep()!!, 1e-9)
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
    fun `every Packet strike hold suppresses ambient movement packets`() {
        assertTrue(shouldSuppressSpearKillStrikeHoldPacket(holdingStrike = true))
        assertFalse(shouldSuppressSpearKillStrikeHoldPacket(holdingStrike = false))
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
    fun `physical packet return begins only at a confirmed route position`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertVec3Equals(
            origin.add(4.0, 2.0, 0.0),
            positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3(4.0, 2.0, 0.0))!!,
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
            positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3(4.0, 2.0, 0.0))!!,
            1e-9,
        )
        assertVec3Equals(origin, positioner.resolve(origin, origin.add(4.0, 2.0, 0.0), Vec3.ZERO)!!, 1e-9)
    }

    @Test
    fun `natural Elytra displacement never claims a virtual return position`() {
        val positioner = SpearKillPhysicalReturnPositioner()
        val origin = Vec3(10.0, 64.0, -3.0)

        assertEquals(
            null,
            positioner.resolve(
                origin = origin,
                currentPosition = origin.add(5.0, 0.0, 0.0),
                confirmedOffset = Vec3(17.32, 0.0, 0.0),
            ),
        )
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
    fun `server rejection to the route origin stops the stale packet path`() {
        val guard = SpearKillSetbackGuard(guardTicks = 2)
        val localState = PositionMoveRotation(
            Vec3(10.0, 64.0, 2.0),
            Vec3.ZERO,
            0f,
            0f,
        )
        guard.record(Vec3(27.32, 64.0, 2.0), localState.position)

        val restore = guard.localRestoreFor(
            localState,
            ClientboundPlayerPositionPacket(
                12,
                PositionMoveRotation(localState.position, Vec3.ZERO, 0f, 0f),
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

        assertEquals(Rotation.fromRotationVec(Vec3(3.0, 0.0, 0.0)), session.pathHeading)
        session.prepareNextStep()
        val forwardHeading = session.pathHeading!!
        assertEquals(Rotation.fromRotationVec(Vec3(3.0, 0.0, 0.0)), forwardHeading)
        session.confirmStep(delivered = true)
        assertNull(session.prepareNextStep())
        assertEquals(Rotation.fromRotationVec(Vec3(0.0, 2.0, 3.0)), session.pathHeading)

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
    fun `Packet pre-strike hold locks rotation onto the terminal lunge`() {
        val approachMovement = Vec3(0.0, 0.0, 4.0)
        val terminalMovement = Vec3(4.0, 0.0, 0.0)
        val session = SpearKillPacketBootSession()
        session.start(
            path = listOf(
                approachMovement,
                terminalMovement,
                terminalMovement.scale(-1.0),
                approachMovement.scale(-1.0),
                Vec3.ZERO,
            ),
            outboundSteps = 2,
            preStrikeHoldTicks = 1,
            terminalSuffixSteps = 1,
        )

        assertEquals(Rotation.fromRotationVec(approachMovement), session.pathHeading)
        session.prepareNextStep()
        session.confirmStep(delivered = true)

        assertEquals(Rotation.fromRotationVec(terminalMovement), session.pathHeading)
        assertNull(session.prepareNextStep())
        assertEquals(Rotation.fromRotationVec(terminalMovement), session.pathHeading)
    }

    @Test
    fun `route rotation override is instant silent and persistent for one tick`() {
        val heading = Rotation(65f, -12f)
        val target = spearKillRouteRotationTarget(heading)

        assertEquals(heading, target.rotation)
        assertTrue(target.processors.isEmpty())
        assertEquals(1, target.ticksUntilReset)
        assertFalse(target.considerInventory)
        assertEquals(MovementCorrection.OFF, target.movementCorrection)
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
