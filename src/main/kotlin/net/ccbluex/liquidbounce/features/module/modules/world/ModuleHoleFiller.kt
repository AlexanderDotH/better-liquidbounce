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
package net.ccbluex.liquidbounce.features.module.modules.world

import it.unimi.dsi.fastutil.booleans.BooleanDoubleImmutablePair
import it.unimi.dsi.fastutil.doubles.DoubleLongPair
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.holefiller.config.HoleFillerFeature
import net.ccbluex.liquidbounce.features.module.modules.world.holefiller.model.HoleFillerPlanContext
import net.ccbluex.liquidbounce.features.module.modules.world.holefiller.planner.SimpleHoleCollector
import net.ccbluex.liquidbounce.features.module.modules.world.holefiller.policy.HoleMovementPolicy
import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.ccbluex.liquidbounce.features.block.hole.HoleManager
import net.ccbluex.liquidbounce.features.block.hole.HoleManagerSubscriber
import net.ccbluex.liquidbounce.features.block.hole.HoleTracker
import net.ccbluex.liquidbounce.features.block.placer.BlockPlacer
import net.ccbluex.liquidbounce.utils.collection.Filter
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.collection.getSlot
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.getBlock
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.expandToBoundingBox
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.math.ceil
import kotlin.math.max

/**
 * Module HoleFiller
 *
 * Automatically fills holes.
 *
 * @author ccetl
 */
object ModuleHoleFiller : ClientModule("HoleFiller", ModuleCategories.WORLD), HoleManagerSubscriber {

    private val features by multiEnumChoice("Features",
        HoleFillerFeature.SMART,
        HoleFillerFeature.PREVENT_SELF_FILL,
        HoleFillerFeature.CHECK_MOVEMENT
    )

    /**
     * The area around entities' feet that will be checked for holes.
     */
    private val fillArea by int("Area", 2, 1..5)

    /**
     * How the blocklist is used.
     */
    private val filter by enumChoice("Filter", Filter.WHITELIST)

    /**
     * Blocks that are used to fill holes, by default just obsidian.
     */
    private val blocks by blocks("Blocks", blockSortedSetOf(Blocks.OBSIDIAN))

    /**
     * The core of the module, the placer.
     */
    private val placer = tree(BlockPlacer(
        "Placing",
        this,
        Priority.NORMAL,
        { filter.getSlot(blocks) },
        allowSupportPlacements = false
    ))

    private val range: Int get() = ceil(max(placer.range, placer.wallRange)).toInt()

    override fun horizontalDistance(): Int = range
    override fun verticalDistance(): Int = range

    override fun onEnabled() {
        HoleManager.subscribe(this)
    }

    override fun onDisabled() {
        HoleManager.unsubscribe(this)
        placer.disable()
    }

    @Suppress("unused")
    private val targetUpdater = handler<RotationUpdateEvent> {
        // all holes, if required 1x1 holes filtered out
        val holes = HoleTracker.holes.filter {
            HoleFillerFeature.ONLY_ONE_BY_ONE !in features || it is Hole.OneByOne
        }

        val blockPos = player.blockPosition()
        val selfInHole = holes.any { it.contains(blockPos) }
        if (HoleFillerFeature.ONLY_WHEN_SELF_IN_HOLE in features && !selfInHole) {
            return@handler
        }

        val selfRegion = blockPos.expandToBoundingBox(fillArea, fillArea, fillArea)

        val blocks = linkedSetOf<BlockPos>()
        val holeContext = HoleFillerPlanContext(holes, selfInHole, selfRegion, blocks)

        if (HoleFillerFeature.SMART !in features) {
            SimpleHoleCollector.collect(
                holeContext,
                HoleFillerFeature.PREVENT_SELF_FILL in features,
                player.y,
            )
        } else {
            val availableItems = getAvailableItemsCount()
            if (availableItems == 0) {
                return@handler
            }

            // the range in which entities are considered as a target
            val range = this.range.sq() + 10.0
            collectHolesSmart(range, holeContext, availableItems)
        }

        placer.update(blocks)
    }

    private fun getAvailableItemsCount(): Int {
        var itemCount = 0
        Slots.OffhandWithHotbar.forEach { slot ->
            val block = slot.itemStack.getBlock() ?: return@forEach
            if (filter(block, blocks)) {
                itemCount += slot.itemStack.count
            }
        }

        return itemCount
    }

    private fun collectHolesSmart(range: Double, holeContext: HoleFillerPlanContext, availableItems: Int) {
        val checkedHoles = hashSetOf<Hole>()
        var remainingItems = availableItems

        world.entitiesForRendering().forEach { entity ->
            if (entity.distanceToSqr(player) > range || entity === player || !entity.shouldBeAttacked()) {
                return@forEach
            }

            val found = hashSetOf<DoubleLongPair>()
            remainingItems = iterateHoles(
                holeContext,
                checkedHoles,
                entity,
                remainingItems,
                found
            )

            found.sortedByDescending { it.leftDouble() }
                .mapTo(holeContext.blocks) { BlockPos.of(it.rightLong()) }
            if (remainingItems <= 0) {
                return
            }
        }
    }

    private fun iterateHoles(
        holeContext: HoleFillerPlanContext,
        checkedHoles: MutableSet<Hole>,
        entity: Entity,
        remainingItems: Int,
        found: MutableSet<DoubleLongPair>
    ): Int {
        var remainingItems1 = remainingItems
        val region = entity.blockPosition().expandToBoundingBox(fillArea, fillArea, fillArea)

        holeContext.holes.forEach { hole ->
            if (hole in checkedHoles) {
               return@forEach
            }

            val valid = isValidHole(hole, entity, region, holeContext.selfInHole, holeContext.selfRegion)
            if (!valid.firstBoolean()) {
                return@forEach
            }

            val holeSize = hole.size
            remainingItems1 -= holeSize
            if (remainingItems1 < 0 && !player.abilities.instabuild) {
                remainingItems1 += holeSize
                return@forEach
            }

            checkedHoles += hole
            hole.asList().mapTo(found) {
                DoubleLongPair.of(valid.rightDouble(), it.asLong())
            }

            if (remainingItems1 == 0 && !player.abilities.instabuild) {
                return 0
            }
        }

        return remainingItems
    }

    private fun isValidHole(
        hole: Hole,
        entity: Entity,
        region: BoundingBox,
        selfInHole: Boolean,
        selfRegion: BoundingBox
    ) : BooleanDoubleImmutablePair {
        val y = hole.pos.y + 1.0
        val movingTowardsHole = HoleMovementPolicy.evaluate(
            hole,
            entity,
            HoleFillerFeature.CHECK_MOVEMENT in features,
        )
        val requirementsMet = movingTowardsHole.firstBoolean() && hole.positions.intersects(region) && y <= entity.y

        val noSelfFillViolation =
            HoleFillerFeature.PREVENT_SELF_FILL !in features
            || y > player.y
            || selfInHole
            || !hole.positions.intersects(selfRegion)

        return BooleanDoubleImmutablePair(requirementsMet && noSelfFillViolation, movingTowardsHole.rightDouble())
    }

}
