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

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InteractableTargetResolverTest {

    @Test
    fun `normal interaction wins without performing the extended raycast`() {
        val world = FakeWorldAdapter(blockHit())
        val resolver = InteractableTargetResolver(world)

        val result = resolver.acquire(request(normalInteractionAvailable = true))

        assertRejected(InteractableTargetRejection.NORMAL_INTERACTION_PRIORITY, result)
        assertEquals(0, world.raycastCalls)
    }

    @Test
    fun `visible menu providers include storage and workstation blocks`() {
        listOf("minecraft:chest", "minecraft:barrel", "minecraft:crafting_table", "minecraft:stonecutter").forEach {
            blockKey ->
            val hit = blockHit(blockKey = blockKey)
            val result = InteractableTargetResolver(FakeWorldAdapter(hit)).acquire(request())

            val acquired = assertIs<InteractableTargetResolution.Acquired>(result)
            assertEquals(hit.observation.toLock(), acquired.target.lock)
            assertEquals(hit.hitLocation, acquired.target.initialHitLocation)
        }
    }

    @Test
    fun `blacklist and whitelist filters are applied to menu providers`() {
        val chest = InteractableBlockKey("minecraft:chest")
        val hit = blockHit(blockKey = chest.value)

        val emptyBlacklist = resolverFor(hit).acquire(request(
            blockFilter = InteractableTargetBlockFilter.blacklist(),
        ))
        assertIs<InteractableTargetResolution.Acquired>(emptyBlacklist)

        val blacklisted = resolverFor(hit).acquire(request(
            blockFilter = InteractableTargetBlockFilter.blacklist(setOf(chest)),
        ))
        assertRejected(InteractableTargetRejection.FILTERED, blacklisted)

        val whitelisted = resolverFor(hit).acquire(request(
            blockFilter = InteractableTargetBlockFilter.whitelist(setOf(chest)),
        ))
        assertIs<InteractableTargetResolution.Acquired>(whitelisted)

        val absentFromWhitelist = resolverFor(hit).acquire(request(
            blockFilter = InteractableTargetBlockFilter.whitelist(setOf(InteractableBlockKey("minecraft:barrel"))),
        ))
        assertRejected(InteractableTargetRejection.FILTERED, absentFromWhitelist)
    }

    @Test
    fun `unloaded outside-border blocked and ordinary blocks are rejected`() {
        val cases = listOf(
            blockHit(loaded = false) to InteractableTargetRejection.UNLOADED,
            blockHit(insideWorldBorder = false) to InteractableTargetRejection.OUTSIDE_WORLD_BORDER,
            blockHit(blocked = true, menuProviderAvailable = false) to InteractableTargetRejection.BLOCKED,
            blockHit(blockKey = "minecraft:stone", menuProviderAvailable = false) to
                InteractableTargetRejection.NOT_MENU_PROVIDER,
        )

        cases.forEach { (hit, expected) ->
            assertRejected(expected, resolverFor(hit).acquire(request()))
        }
    }

    @Test
    fun `only the supported vanilla container vehicles are acquired`() {
        val supported = listOf(
            InteractableEntityKind.CONTAINER_MINECART,
            InteractableEntityKind.CHEST_BOAT,
            InteractableEntityKind.CHEST_RAFT,
        )

        supported.forEach { kind ->
            val hit = entityHit(kind = kind)
            val acquired = assertIs<InteractableTargetResolution.Acquired>(resolverFor(hit).acquire(request()))
            assertEquals(hit.observation.toLock(), acquired.target.lock)
        }

        assertRejected(
            InteractableTargetRejection.UNSUPPORTED_ENTITY,
            resolverFor(entityHit(kind = InteractableEntityKind.UNSUPPORTED)).acquire(request()),
        )
        assertRejected(
            InteractableTargetRejection.CONTAINER_VEHICLES_DISABLED,
            resolverFor(entityHit()).acquire(request(containerVehicles = false)),
        )
    }

    @Test
    fun `removed dead unloaded and outside-border container vehicles are rejected`() {
        val cases = listOf(
            entityHit(alive = false) to InteractableTargetRejection.TARGET_REMOVED,
            entityHit(removed = true) to InteractableTargetRejection.TARGET_REMOVED,
            entityHit(loaded = false) to InteractableTargetRejection.UNLOADED,
            entityHit(insideWorldBorder = false) to InteractableTargetRejection.OUTSIDE_WORLD_BORDER,
        )

        cases.forEach { (hit, expected) ->
            assertRejected(expected, resolverFor(hit).acquire(request()))
        }
    }

    @Test
    fun `invalid player and conflicting movement states reject before raycasting`() {
        val cases = listOf(
            InteractablePlayerEligibility(alive = false) to InteractableTargetRejection.PLAYER_DEAD,
            InteractablePlayerEligibility(spectator = true) to InteractableTargetRejection.SPECTATOR,
            InteractablePlayerEligibility(passenger = true) to InteractableTargetRejection.PASSENGER,
            InteractablePlayerEligibility(detachedCamera = true) to InteractableTargetRejection.DETACHED_CAMERA,
            InteractablePlayerEligibility(remoteMovementAvailable = false) to
                InteractableTargetRejection.REMOTE_MOVEMENT_BUSY,
        )

        cases.forEach { (eligibility, expected) ->
            val world = FakeWorldAdapter(blockHit())
            assertRejected(expected, InteractableTargetResolver(world).acquire(request(player = eligibility)))
            assertEquals(0, world.raycastCalls)
        }
    }

    @Test
    fun `invalid configured ranges reject before raycasting`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEach { maxRange ->
            val world = FakeWorldAdapter(blockHit())
            assertRejected(
                InteractableTargetRejection.INVALID_RANGE,
                InteractableTargetResolver(world).acquire(request(maxRange = maxRange)),
            )
            assertEquals(0, world.raycastCalls)
        }
    }

    @Test
    fun `occluded block menu providers continue to route plausibility while entities stay visible only`() {
        assertIs<InteractableTargetResolution.Acquired>(
            resolverFor(blockHit(visible = false)).acquire(request()),
        )
        assertRejected(
            InteractableTargetRejection.OCCLUDED,
            resolverFor(entityHit(visible = false)).acquire(request()),
        )
    }

    @Test
    fun `out-of-range misses and unavailable worlds are rejected`() {
        assertRejected(
            InteractableTargetRejection.OUT_OF_RANGE,
            resolverFor(blockHit(distanceSquared = 257.0 * 257.0)).acquire(request(maxRange = 256.0)),
        )
        assertRejected(
            InteractableTargetRejection.MISS,
            resolverFor(InteractableRayHit.Miss).acquire(request()),
        )
        assertRejected(
            InteractableTargetRejection.WORLD_UNAVAILABLE,
            resolverFor(InteractableRayHit.WorldUnavailable).acquire(request()),
        )
    }

    @Test
    fun `through-wall ray skips ordinary walls but retains menu blocks and blocked chests`() {
        assertEquals(false, isPotentialOccludedMenuTarget(
            hasMenuProvider = false,
            opensMenuWithoutProvider = false,
            isChest = false,
        ))
        assertEquals(true, isPotentialOccludedMenuTarget(
            hasMenuProvider = true,
            opensMenuWithoutProvider = false,
            isChest = false,
        ))
        assertEquals(true, isPotentialOccludedMenuTarget(
            hasMenuProvider = false,
            opensMenuWithoutProvider = false,
            isChest = true,
        ))
    }

    @Test
    fun `block lock requires the exact position and block state`() {
        val hit = blockHit()
        val lock = assertIs<InteractableTargetResolution.Acquired>(resolverFor(hit).acquire(request())).target.lock
        val world = FakeWorldAdapter(hit).apply {
            observation = hit.observation
        }
        val resolver = InteractableTargetResolver(world)

        assertEquals(InteractableTargetValidation.Valid, resolver.validate(lock))

        world.observation = hit.observation.copy(
            identity = hit.observation.identity?.copy(stateKey = InteractableBlockStateKey(99)),
        )
        assertInvalid(InteractableTargetRejection.TARGET_CHANGED, resolver.validate(lock))
        assertEquals(
            InteractableTargetValidation.Valid,
            resolver.validate(lock, allowInteractionStateChange = true),
        )

        world.observation = hit.observation.copy(blocked = true, menuProviderAvailable = false)
        assertInvalid(InteractableTargetRejection.BLOCKED, resolver.validate(lock))

        world.observation = hit.observation.copy(loaded = false)
        assertInvalid(InteractableTargetRejection.UNLOADED, resolver.validate(lock))

        world.observation = hit.observation.copy(insideWorldBorder = false)
        assertInvalid(InteractableTargetRejection.OUTSIDE_WORLD_BORDER, resolver.validate(lock))
    }

    @Test
    fun `vehicle lock uses uuid and kind and rejects removal or replacement`() {
        val hit = entityHit()
        val lock = assertIs<InteractableTargetResolution.Acquired>(resolverFor(hit).acquire(request())).target.lock
        val world = FakeWorldAdapter(hit).apply {
            observation = hit.observation
        }
        val resolver = InteractableTargetResolver(world)

        assertEquals(InteractableTargetValidation.Valid, resolver.validate(lock))

        world.observation = hit.observation.copy(removed = true)
        assertInvalid(InteractableTargetRejection.TARGET_REMOVED, resolver.validate(lock))

        world.observation = hit.observation.copy(uuid = UUID.randomUUID())
        assertInvalid(InteractableTargetRejection.TARGET_CHANGED, resolver.validate(lock))

        world.observation = hit.observation.copy(
            position = InteractableTargetPoint(21.0, 65.0, 20.0),
        )
        assertInvalid(InteractableTargetRejection.TARGET_CHANGED, resolver.validate(lock))

        world.observation = InteractableTargetObservation.Missing
        assertInvalid(InteractableTargetRejection.TARGET_MISSING, resolver.validate(lock))
    }

    private fun resolverFor(hit: InteractableRayHit) = InteractableTargetResolver(FakeWorldAdapter(hit))

    private fun request(
        maxRange: Double = 256.0,
        normalInteractionAvailable: Boolean = false,
        player: InteractablePlayerEligibility = InteractablePlayerEligibility(),
        containerVehicles: Boolean = true,
        blockFilter: InteractableTargetBlockFilter = InteractableTargetBlockFilter.blacklist(),
    ) = InteractableTargetRequest(
        maxRange = maxRange,
        normalInteractionAvailable = normalInteractionAvailable,
        player = player,
        containerVehicles = containerVehicles,
        blockFilter = blockFilter,
    )

    private fun blockHit(
        blockKey: String = "minecraft:chest",
        loaded: Boolean = true,
        insideWorldBorder: Boolean = true,
        blocked: Boolean = false,
        menuProviderAvailable: Boolean = true,
        visible: Boolean = true,
        distanceSquared: Double = 100.0,
    ): InteractableRayHit.Block {
        val identity = InteractableBlockIdentity(
            blockKey = InteractableBlockKey(blockKey),
            stateKey = InteractableBlockStateKey(42),
        )
        return InteractableRayHit.Block(
            observation = InteractableTargetObservation.Block(
                position = InteractableBlockPosition(12, 64, -3),
                identity = identity.takeIf { loaded },
                loaded = loaded,
                insideWorldBorder = insideWorldBorder,
                menuProviderAvailable = menuProviderAvailable,
                blocked = blocked,
            ),
            hitLocation = InteractableTargetPoint(12.5, 64.5, -2.99),
            distanceSquared = distanceSquared,
            visible = visible,
        )
    }

    private fun entityHit(
        kind: InteractableEntityKind = InteractableEntityKind.CONTAINER_MINECART,
        alive: Boolean = true,
        removed: Boolean = false,
        loaded: Boolean = true,
        insideWorldBorder: Boolean = true,
        visible: Boolean = true,
    ): InteractableRayHit.Entity {
        return InteractableRayHit.Entity(
            observation = InteractableTargetObservation.Entity(
                uuid = UUID.fromString("4b5aa22c-81fd-4e23-a4ea-d7360ed139b6"),
                kind = kind,
                alive = alive,
                removed = removed,
                loaded = loaded,
                insideWorldBorder = insideWorldBorder,
                position = InteractableTargetPoint(20.0, 65.0, 20.0),
            ),
            hitLocation = InteractableTargetPoint(20.0, 65.0, 20.0),
            distanceSquared = 200.0,
            visible = visible,
        )
    }

    private fun assertRejected(
        reason: InteractableTargetRejection,
        result: InteractableTargetResolution,
    ) {
        assertEquals(reason, assertIs<InteractableTargetResolution.Rejected>(result).reason)
    }

    private fun assertInvalid(
        reason: InteractableTargetRejection,
        result: InteractableTargetValidation,
    ) {
        assertEquals(reason, assertIs<InteractableTargetValidation.Invalid>(result).reason)
    }

    private class FakeWorldAdapter(
        private val hit: InteractableRayHit,
    ) : InteractableTargetWorldAdapter {
        var raycastCalls = 0
        var observation: InteractableTargetObservation = InteractableTargetObservation.Missing

        override fun raycast(maxRange: Double): InteractableRayHit {
            raycastCalls++
            return hit
        }

        override fun observe(lock: InteractableTargetLock): InteractableTargetObservation = observation
    }
}
