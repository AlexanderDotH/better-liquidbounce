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
package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.GenericDebugRecorder
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.FallingPlayer
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun CriticalsJump.evaluateCriticalWait(target: Entity): Boolean {
    val onGround = player.onGround()
    val isJumping = player.input.keyPresses.jump || automaticJumpPending
    if (onGround && !isJumping) return false

    val nextPossibleCrit = calculateCriticalsJumpTicksUntilNextCrit()
    return if (!onGround && player.deltaMovement.y <= 0.0) {
        shouldWaitWhileFalling(nextPossibleCrit)
    } else {
        shouldWaitWhileRising(target, onGround, nextPossibleCrit)
    }
}

private fun shouldWaitWhileFalling(nextPossibleCrit: Float): Boolean {
    val collision = FallingPlayer.fromPlayer(player).findCollision((nextPossibleCrit + 1.0f).toInt())
    return shouldWaitForFallingCritical(
        player.fallDistance,
        player.getAttackStrengthScale(0.5f),
        nextPossibleCrit,
        collision?.tick,
    )
}

internal fun shouldWaitForFallingCritical(
    fallDistance: Double,
    attackStrength: Float,
    nextPossibleCrit: Float,
    collisionTick: Int?,
): Boolean {
    if (fallDistance > 0.0 && attackStrength > 0.9f) return false
    return collisionTick == null || collisionTick >= nextPossibleCrit.toInt()
}

private fun CriticalsJump.shouldWaitWhileRising(
    target: Entity,
    onGround: Boolean,
    nextPossibleCrit: Float,
): Boolean {
    val initialMotionY = if (onGround) configuredHeight.toDouble() else player.deltaMovement.y
    val ticksTillFall = (initialMotionY / CRITICALS_JUMP_GRAVITY).toFloat()
    val ticksTillCrit = nextPossibleCrit.coerceAtLeast(ticksTillFall)
    val (simulatedPlayerPos, simulatedTargetPos) = predictCriticalsJumpPlayerPositions(target, ticksTillCrit.toInt())
    recordCriticalsJumpPrediction(target, ticksTillCrit, simulatedPlayerPos, simulatedTargetPos)
    val fallingPlayer = criticalsJumpFallingPlayer(onGround, initialMotionY)
    val collision = fallingPlayer.findCollision((ticksTillCrit + 5.0f).toInt())
    return canWaitThroughRisingCritical(ticksTillFall, collision?.tick)
}

internal fun canWaitThroughRisingCritical(ticksTillFall: Float, collisionTick: Int?): Boolean =
    collisionTick == null || collisionTick >= ticksTillFall.toInt()

internal fun calculateCriticalsJumpTicksUntilNextCrit(): Float {
    val durationToWait = player.currentItemAttackStrengthDelay * 0.9F - 0.5F
    val waitedDuration = player.attackStrengthTicker.toFloat()
    return (durationToWait - waitedDuration).coerceAtLeast(0.0f)
}

private fun predictCriticalsJumpPlayerPositions(target: Entity, ticks: Int): Pair<Vec3, Vec3> =
    if (target is Player) predictCriticalsJumpPlayerPositions(target, ticks) else player.position() to target.position()

private fun predictCriticalsJumpPlayerPositions(target: Player, ticks: Int): Pair<Vec3, Vec3> {
    val simulatedPlayer = SimulatedPlayer.fromClientPlayer(
        SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(DirectionalInput(player.input)),
    )
    val simulatedTarget = SimulatedPlayer.fromOtherPlayer(
        target,
        SimulatedPlayer.SimulatedPlayerInput.guessInput(target),
    )
    repeat(ticks) { tick ->
        if (tick == CRITICALS_JUMP_REACTION_TICKS) {
            simulatedPlayer.yRot = Rotation.lookingAt(
                point = target.position(),
                from = simulatedPlayer.pos,
            ).yRot
        }
        simulatedPlayer.tick()
        simulatedTarget.tick()
    }
    return simulatedPlayer.pos to simulatedTarget.pos
}

private fun recordCriticalsJumpPrediction(
    target: Entity,
    ticksTillCrit: Float,
    simulatedPlayerPos: Vec3,
    simulatedTargetPos: Vec3,
) {
    ModuleDebug.debugParameter(ModuleCriticals, "timeToCrit", ticksTillCrit)
    GenericDebugRecorder.recordDebugInfo(ModuleCriticals, "critEstimation", JsonObject().apply {
        addProperty("ticksTillCrit", ticksTillCrit)
        add("player", GenericDebugRecorder.debugObject(player))
        add("target", GenericDebugRecorder.debugObject(target))
        addProperty("simulatedPlayerPos", simulatedPlayerPos.toString())
        addProperty("simulatedTargetPos", simulatedTargetPos.toString())
    })
    GenericDebugRecorder.debugEntityIn(target, ticksTillCrit.toInt())
}

private fun CriticalsJump.criticalsJumpFallingPlayer(onGround: Boolean, initialMotionY: Double): FallingPlayer =
    if (onGround) {
        FallingPlayer(
            player,
            player.x,
            player.y,
            player.z,
            player.deltaMovement.x,
            player.deltaMovement.y + initialMotionY,
            player.deltaMovement.z,
            player.yRot,
        )
    } else {
        FallingPlayer.fromPlayer(player)
    }

private const val CRITICALS_JUMP_GRAVITY = 0.08
private const val CRITICALS_JUMP_REACTION_TICKS = 10
