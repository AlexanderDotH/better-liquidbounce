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
package net.ccbluex.liquidbounce.features.block.placer

import it.unimi.dsi.fastutil.longs.Long2BooleanLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.render.placement.PlacementRenderer
import net.minecraft.core.BlockPos

class BlockPlacer(
    name: String,
    val module: EventListener,
    val priority: Priority,
    val slotFinder: (BlockPos?) -> HotbarItemSlot?,
    allowSupportPlacements: Boolean = true
) : ValueGroup(name), EventListener {

    val range by float("Range", 4.5f, 1f..6f)
    val wallRange by float("WallRange", 4.5f, 0f..6f)
    val cooldown by intRange("Cooldown", 1..2, 0..40, "ticks")
    val swingMode by enumChoice("Swing", SwingMode.DO_NOT_HIDE)

    /**
     * Construct a hit result at the point selected by target finding when the raytrace result is invalid.
     * This can make the module rotations wrong as well as place a bit outside the range,
     * but it makes the placements a lot more reliable and works on most servers.
     */
    val constructFailResult by boolean("ConstructFailResult", true)

    /**
     * Defines how long the player should sneak when placing on an interactable block.
     * This can make placing multiple blocks seem smoother.
     */
    internal val sneak by intRange("Sneak", 1..1, 0..10, "ticks")

    private val ignores by multiEnumChoice("Ignore", Ignore.entries)

    val ignoreOpenInventory get() = Ignore.OPEN_INVENTORY in ignores

    val ignoreUsingItem get() = Ignore.USING_ITEM in ignores

    val slotResetDelay by intRange("SlotResetDelay", 4..6, 0..40, "ticks")

    val rotationMode = modes(this, "RotationMode") {
        arrayOf(NormalRotationMode(it, this), NoRotationMode(it, this))
    }

    val support = SupportFeature(this)

    init {
        if (allowSupportPlacements) {
            tree(support)
        } else {
            support.enabled = false
        }
    }

    val crystalDestroyer = tree(CrystalDestroyFeature(this, module))

    /**
     * Renders all tracked positions that are queued to be placed.
     */
    val targetRenderer = tree(PlacementRenderer("TargetRendering", false, module))

    /**
     * Renders all placements.
     */
    val placedRenderer = tree(PlacementRenderer(
        "PlacedRendering",
        true,
        module,
        keep = false
    ))

    internal val blockPosCache = BlockPos.MutableBlockPos()

    /**
     * Stores all block positions where blocks should be placed paired with a boolean that is `true`
     * if the position was added by [support].
     */
    val blocks = Long2BooleanLinkedOpenHashMap()

    internal val inaccessible = LongOpenHashSet()
    var ticksToWait = 0
    var ranAction = false
    internal var sneakTimes = 0

    @Suppress("unused")
    private val targetUpdater = handler<RotationUpdateEvent>(priority = -20) {
        handleTargetUpdate()
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        handleMovementInput(event)
    }

    fun doPlacement(isSupport: Boolean, pos: BlockPos, placementTarget: BlockPlacementTarget): Boolean =
        performPlacement(isSupport, pos, placementTarget)

    fun canReach(pos: BlockPos, rotation: Rotation): Boolean = canReachInternal(pos, rotation)

    fun update(positions: Collection<BlockPos>) = updateQueue(positions)

    fun addToQueue(pos: BlockPos, update: Boolean = true, isSupport: Boolean = false) =
        addQueue(pos, update, isSupport)

    fun removeFromQueue(pos: BlockPos) = removeQueue(pos)

    fun clear() = clearQueue()

    fun disable() = disablePlacer()

    fun isDone(): Boolean = isQueueDone()

    @Suppress("unused")
    val worldChangeHandler = handler<WorldChangeEvent> {
        resetState()
    }

    override fun parent(): EventListener = module

    private enum class Ignore(override val tag: String) : Tagged {
        OPEN_INVENTORY("OpenInventory"),
        USING_ITEM("UsingItem")
    }
}
