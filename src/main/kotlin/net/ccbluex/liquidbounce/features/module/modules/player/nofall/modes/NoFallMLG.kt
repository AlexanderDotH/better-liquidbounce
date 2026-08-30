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
package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleFreeze
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.ccbluex.liquidbounce.features.rotation.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.block.liquid.TimedPickupTracker
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.isOlderThan1_21
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.FINAL_DECISION
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.world.waterEvaporates
import net.minecraft.core.BlockPos
import net.minecraft.world.item.Items

internal object NoFallMLG : NoFallMode("MLG") {
    internal const val SCAFFOLDING_ATTEMPT_TIMEOUT_TICKS = 20

    internal val minFallDist by float("MinFallDistance", 5f, 2f..50f)

    internal object PickupWater : ToggleableValueGroup(NoFallMLG, "PickUpWater", true) {
        val pickupSpan by intRange("PickupSpan", 200..1000, 0..10000, "ms")
    }

    internal val rotations = tree(RotationsValueGroup(this))
    internal var currentTarget: MlgPlacementAction? = null
    internal val pickupTracker = TimedPickupTracker(PICKUP_TRACKER_CAPACITY)
    internal var scaffoldingTarget: BlockPos? = null
    internal var scaffoldingPlacedAtTick = 0
    internal var forceSneak = false
    internal val safeFallDistance: Double
        get() = playerSafeFallDistance

    private val netherItems = setOf(
        Items.SCAFFOLDING,
        Items.COBWEB,
        Items.POWDER_SNOW_BUCKET,
        Items.HAY_BLOCK,
        Items.SLIME_BLOCK,
        Items.HONEY_BLOCK,
        Items.TWISTING_VINES,
    )
    private val normalItems = netherItems + Items.WATER_BUCKET
    internal val itemsForMLG
        get() = if (world.waterEvaporates) netherItems else normalItems

    init {
        tree(PickupWater)
    }

    override val running: Boolean
        get() = super.running && !ModuleFreeze.running

    override fun disable() {
        SilentHotbar.resetSlot(this)
        resetState()
    }

    private fun resetState() {
        currentTarget = null
        pickupTracker.clear()
        scaffoldingTarget = null
        forceSneak = false
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetState()
    }

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(priority = FINAL_DECISION) { event ->
        if (forceSneak) event.sneak = true
    }

    @Suppress("unused")
    private val tickMovementHandler = handler<RotationUpdateEvent> {
        forceSneak = false
        if (maintainScaffoldingAttempt()) {
            currentTarget = null
            return@handler
        }
        val currentGoal = getCurrentGoal()
        if (currentGoal == null || !shouldPrepareMlgAction(
                currentGoal.collisionTick,
                rotations.calculateTicks(currentGoal.plan.placementTarget.rotation),
                currentGoal.requiresSneak,
                player.isShiftKeyDown,
            )
        ) {
            currentTarget = null
            return@handler
        }
        forceSneak = currentGoal.requiresSneak
        currentTarget = currentGoal.takeUnless { it.requiresSneak && !player.isShiftKeyDown }
        RotationManager.setRotationTarget(
            currentGoal.plan.placementTarget.rotation,
            valueGroup = rotations,
            priority = Priority.IMPORTANT_FOR_PLAYER_LIFE,
            provider = ModuleNoFall,
        )
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        val action = currentTarget ?: return@handler
        if (isOlderThan1_21) {
            currentTarget = null
            PostRotationExecutor.addTask(ModuleNoFall, postMove = true, priority = true) {
                executePlacement(action)
            }
            return@handler
        }
        executePlacement(action)
    }

    private const val PICKUP_TRACKER_CAPACITY = 8
}
