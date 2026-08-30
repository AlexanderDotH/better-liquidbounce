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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.SprintEvent
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.entity.doesCollideAt
import net.ccbluex.liquidbounce.utils.entity.getMovementDirectionOfInput
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.math.fma
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.movement.getDegreesRelativeToView
import net.ccbluex.liquidbounce.utils.movement.getDirectionalInputForDegrees
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

internal fun FightBotRuntime.handleMovementInput(event: MovementInputEvent) {
    if (!combatOperational) return
    val context = createNavigationContext()
    val goal = calculateGoalPosition(context) ?: return
    debug.point("Goal", goal, FightBotDebugColor.Blue, size = 0.4)
    event.directionalInput = calculateDirectionalInput(event.directionalInput, goal)
    applyMovementAssist(event, context)
}

internal fun FightBotRuntime.handleSprint(event: SprintEvent) {
    if (!combatOperational || FightBotAutoAction.SPRINT !in settings.automaticActions ||
        !event.directionalInput.isMoving
    ) {
        return
    }
    if (event.source == SprintEvent.Source.MOVEMENT_TICK || event.source == SprintEvent.Source.INPUT) {
        event.sprint = true
    }
}

internal fun FightBotRuntime.movementRotation(): Rotation {
    val movementRotation = Rotation(player.getMovementDirectionOfInput(), 0.0f)
    val movementPitch = targetTracker.target?.let { entity ->
        Rotation.lookingAt(point = entity.boundingBox.center, from = player.eyePosition).pitch
    } ?: return movementRotation
    return movementRotation.copy(pitch = movementPitch)
}

private fun FightBotRuntime.createNavigationContext(): FightBotCombatContext {
    val playerPosition = player.position()
    val combatTarget = targetTracker.target?.let { entity ->
        val distance = playerPosition.distanceTo(entity.position())
        val range = min(combat.interactionRange, distance.toFloat())
        val targetRotation = entity.rotation.copy(pitch = 0.0f)
        val requiredRotation = Rotation.lookingAt(playerPosition, entity.eyePosition).copy(pitch = 0.0f)
        FightBotCombatTarget(
            entity = entity,
            range = range,
            outOfDistance = distance > settings.opponentRange,
            targetRotation = targetRotation,
            requiredTargetRotation = requiredRotation,
            outOfDanger = abs(targetRotation.rotationDeltaTo(requiredRotation).deltaYaw) > settings.dangerousYaw,
        )
    }
    return FightBotCombatContext(playerPosition, combatTarget)
}

private fun FightBotRuntime.calculateGoalPosition(context: FightBotCombatContext): Vec3? {
    if (settings.leaderRunning && settings.leaderUsername.isNotEmpty()) {
        world.players().find { it.gameProfile.name == settings.leaderUsername }?.let { leader ->
            return calculateLeaderGoalPosition(leader.position(), context.playerPosition)
        }
    }
    val combatTarget = context.combatTarget ?: return null
    return if (settings.runawayOnCooldown && !combat.willClickAt()) {
        context.playerPosition.fma(combatTarget.range.toDouble(), combatTarget.requiredTargetRotation.directionVector)
    } else {
        calculateAttackPosition(context, combatTarget)
    }
}

private fun FightBotRuntime.applyMovementAssist(event: MovementInputEvent, context: FightBotCombatContext) {
    if ((FightBotAutoAction.SWIM in settings.automaticActions && player.isInWater) ||
        (FightBotAutoAction.JUMP in settings.automaticActions && player.horizontalCollision)
    ) {
        event.jump = true
    }
    val targetAllowsJump = context.combatTarget?.let { it.outOfDistance && !it.outOfDanger } == true
    val goal = calculateGoalPosition(context) ?: return
    val leaderAllowsJump = settings.leaderRunning &&
        player.position().distanceTo(goal) > settings.leaderRadius
    if (targetAllowsJump || leaderAllowsJump) event.jump = true
}

private fun FightBotRuntime.calculateDirectionalInput(
    currentInput: DirectionalInput,
    goal: Vec3,
): DirectionalInput {
    val degrees = getDegreesRelativeToView(goal.subtract(player.position()), player.yRot)
    return getDirectionalInputForDegrees(currentInput, degrees, deadAngle = 20.0F)
}

private fun FightBotRuntime.calculateLeaderGoalPosition(leaderPosition: Vec3, playerPosition: Vec3): Vec3 =
    (-180..180 step 45).map { yaw ->
        val position = leaderPosition.fma(
            settings.leaderRadius.toDouble(),
            Rotation(yaw.toFloat(), 0.0F).directionVector,
        )
        debug.point(
            "Possible Position $yaw",
            position,
            FightBotDebugColor.Magenta,
            size = 0.2,
        )
        position
    }.minByOrNull { it.distanceToSqr(playerPosition) } ?: leaderPosition

private fun FightBotRuntime.calculateAttackPosition(
    context: FightBotCombatContext,
    combatTarget: FightBotCombatTarget,
): Vec3 {
    val target = combatTarget.entity
    val targetLookPosition = target.position().fma(combatTarget.range.toDouble(), combatTarget.targetRotation.directionVector)
    return (-180..180 step 10).mapNotNull { yaw ->
        val rotation = Rotation(yaw.toFloat(), 0.0F)
        val position = target.position().fma(combatTarget.range.toDouble(), rotation.directionVector)
        if (player.doesCollideAt(position)) return@mapNotNull null
        val dangerous = abs(rotation.rotationDeltaTo(combatTarget.targetRotation).deltaYaw) <= settings.dangerousYaw
        debug.point(
            "Possible Position $yaw",
            position,
            if (dangerous) FightBotDebugColor.Red else FightBotDebugColor.Green,
            size = 0.2,
        )
        position.takeUnless { dangerous }
    }.sortedBy { it.distanceToSqr(targetLookPosition) }
        .minByOrNull { it.distanceToSqr(context.playerPosition) }
        ?: targetLookPosition
}

private data class FightBotCombatContext(
    val playerPosition: Vec3,
    val combatTarget: FightBotCombatTarget?,
)

private data class FightBotCombatTarget(
    val entity: LivingEntity,
    val range: Float,
    val outOfDistance: Boolean,
    val targetRotation: Rotation,
    val requiredTargetRotation: Rotation,
    val outOfDanger: Boolean,
)
