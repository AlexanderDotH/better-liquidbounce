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
package net.ccbluex.liquidbounce.features.module.modules.world.autofarm

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.utils.asRefreshable
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug.debugGeometry
import net.ccbluex.liquidbounce.features.module.modules.world.autofarm.planner.TargetSelector
import net.ccbluex.liquidbounce.features.module.modules.world.autofarm.planner.TargetingContext
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.features.block.runtime.ChunkScanner
import net.ccbluex.liquidbounce.features.block.runtime.doBreak
import net.ccbluex.liquidbounce.features.block.runtime.doPlacement
import net.ccbluex.liquidbounce.utils.block.state
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.ccbluex.liquidbounce.utils.inventory.hasInventorySpace
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPoint
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult

/**
 * AutoFarm module
 *
 * Automatically farms stuff for you.
 */
object ModuleAutoFarm : ClientModule("AutoFarm", ModuleCategories.WORLD) {

    private val range by float("Range", 5F, 1F..6F)
    private val wallRange by float("WallRange", 0f, 0F..6F).onChange {
        minOf(it, range)
    }

    // The ticks to wait after interacting with something
    private val interactDelay by intRange("InteractDelay", 2..3, 1..15, "ticks")

    private val disableOnFullInventory by boolean("DisableOnFullInventory", false)

    private object AutoPlaceCrops : ToggleableValueGroup(this, "AutoPlant", true, aliases = listOf("AutoPlace")) {
        val swapBackDelay by intRange("SwapBackDelay", 1..2, 1..20, "ticks")
    }

    internal object AutoUseBoneMeal : ToggleableValueGroup(this, "AutoUseBoneMeal", false) {
        private val chronometer = Chronometer()
        // TODO Use filter (wheat/potato/...)
        private val useDelay = intRange("UseDelay", 20..200, 0..20000, "ms").asRefreshable()
        val swapBackDelay by intRange("SwapBackDelay", 1..2, 1..20, "ticks")

        val isReady get() = chronometer.hasElapsed(useDelay.current.toLong())

        fun reset() {
            chronometer.reset()
            useDelay.refresh()
        }
    }

    private val fortune by boolean("UseFortune", true)

    init {
        tree(AutoFarmAutoWalk)
        tree(AutoPlaceCrops)
        tree(AutoUseBoneMeal)
        tree(AutoFarmVisualizer)
    }

    internal val rotations = tree(RotationsValueGroup(this))

    private fun swapToSlotWithFortune() {
        if (!fortune) {
            return
        }
        // Swap to a fortune item to increase drops
        Slots.Hotbar.maxByOrNull { it.itemStack.getEnchantment(Enchantments.FORTUNE) }
            ?.takeIf { it.itemStack.getEnchantment(Enchantments.FORTUNE) >= 1 }
            ?.let {
                SilentHotbar.selectSlotSilently(this, it, 2)
            }
    }

    var currentTarget: BlockPos? = null
        private set

    // Find the target on RotationUpdateEvent, which runs right before RotationManager.update, so the
    // requested rotation is applied within the same tick.
    @Suppress("unused")
    private val rotationUpdateHandler = handler<RotationUpdateEvent> {
        // Return if the user is inside a screen like the inventory
        if (mc.gui.screen() is AbstractContainerScreen<*>) {
            return@handler
        }

        updateTarget()
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        // Return if the user is inside a screen like the inventory
        if (mc.gui.screen() is AbstractContainerScreen<*>) {
            return@tickHandler
        }

        // Return if the blink module is enabled
        if (ModuleBlink.running) {
            return@tickHandler
        }

        // Disable the module and return if the inventory is full, and the setting for disabling the module is enabled
        if (disableOnFullInventory && !hasInventorySpace()) {
            notification("Inventory is Full", "AutoFarm has been disabled", NotificationEvent.Severity.ERROR)
            onDisabled()
            enabled = false
            return@tickHandler
        }

        // Return if we don't have a target
        val target = currentTarget ?: return@tickHandler

        val rotation = RotationManager.serverRotation
        val rayTraceResult = traceFromPoint(
            range = range.toDouble(),
            start = player.eyePosition,
            // Use the rotation already sent to the server, so we only interact when the server sees the aim
            direction = rotation.directionVector,
            entity = player,
        )
        if (rayTraceResult.type != HitResult.Type.BLOCK) {
            return@tickHandler
        }

        val blockPos = rayTraceResult.blockPos

        // Only act when we are actually aiming at the target block
        if (blockPos != target) {
            return@tickHandler
        }

        val state = blockPos.state ?: return@tickHandler
        if (blockPos.readyForHarvest(state)) {
            when (state.block.harvestAction) {
                HarvestAction.BREAK -> {
                    swapToSlotWithFortune()
                    doBreak(rayTraceResult)
                }
                HarvestAction.USE -> {
                    doPlacement(rayTraceResult, rotation)
                }
                null -> return@tickHandler
            }

            if (interaction.destroyStage == -1) {
                // Only wait if the block is completely broken
                waitTicks(interactDelay.random())
            }
        } else if (AutoUseBoneMeal.enabled && AutoUseBoneMeal.isReady && blockPos.canUseBoneMeal(state)) {
            val boneMealSlot = Slots.OffhandWithHotbar.findClosestSlot { it.item is BoneMealItem } ?: return@tickHandler

            SilentHotbar.selectSlotSilently(this, boneMealSlot, AutoUseBoneMeal.swapBackDelay.random())
            doPlacement(rayTraceResult, rotation, hand = boneMealSlot.useHand)
            AutoUseBoneMeal.reset()
            waitTicks(interactDelay.random())
        } else {
            val blockState = world.getBlockState(blockPos)

            debugGeometry("RayTraceResult") {
                ModuleDebug.DebuggedPoint(rayTraceResult.location, Color4b.RED.alpha(150))
            }
            debugGeometry("PlantablePos") {
                ModuleDebug.DebuggedBox(AABB(blockPos), Color4b.GREEN.alpha(100))
            }

            val sides = AutoFarmTrackedState.Plantable.entries.findPlantableSides(blockPos, blockState)
            if (sides.isNotEmpty()) {
                val slot = AutoFarmTrackedState.Plantable.entries.firstNotNullOfOrNull {
                    if (it.isBlockMatches(blockState)) {
                        Slots.OffhandWithHotbar.findClosestSlot(it.items)
                    } else {
                        null
                    }
                } ?: return@tickHandler

                SilentHotbar.selectSlotSilently(this, slot, AutoPlaceCrops.swapBackDelay.random())
                doPlacement(rayTraceResult, rotation, hand = slot.useHand)

                waitTicks(interactDelay.random())
            }
        }
    }

    /** Prefers harvestable blocks, then plantable blocks, then fertilizable blocks. */
    private fun updateTarget() {
        currentTarget = null
        val context = TargetingContext(player, world, range, wallRange, AutoPlaceCrops.enabled, AutoUseBoneMeal.enabled)
        val target = TargetSelector.select(context, AutoFarmTargetSelectionPolicy)
        currentTarget = target?.blockPos
        target ?: return
        RotationManager.setRotationTarget(
            target.rotation,
            valueGroup = rotations,
            priority = Priority.IMPORTANT_FOR_USAGE_1,
            provider = this@ModuleAutoFarm,
        )
    }

    override fun onEnabled() {
        ChunkScanner.subscribe(AutoFarmBlockTracker)
    }

    override fun onDisabled() {
        ChunkScanner.unsubscribe(AutoFarmBlockTracker)
        currentTarget = null
        AutoUseBoneMeal.reset()
        SilentHotbar.resetSlot(this)
    }

}
