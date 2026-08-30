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
package net.ccbluex.liquidbounce.features.module.modules.combat.criticals.modes

import net.ccbluex.fastutil.component1
import net.ccbluex.fastutil.component2
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerJumpEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAutoClicker
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals.allowsCriticalHit
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.combat.runtime.findEnemies
import net.ccbluex.liquidbounce.utils.entity.FallingPlayer
import net.minecraft.world.entity.Entity

object CriticalsJump : Mode("Jump") {

    override val parent: ModeValueGroup<*>
        get() = ModuleCriticals.modes

    // There are different possible jump heights to crit enemy
    //   Hop: 0.1 (like in Wurst-Client)
    //   LowJump: 0.3425 (for some weird AAC version)
    //
    private val height by float("Height", 0.42f, 0.1f..0.42f)

    // Jump crit should just be active until an enemy is in your reach to be attacked
    private val range by float("Range", 4f, 1f..6f)

    private val optimizeForCooldown by boolean("OptimizeForCooldown", true)

    private val checkKillaura by boolean("CheckKillaura", false)
    private val checkAutoClicker by boolean("CheckAutoClicker", false)
    private val canBeSeen by boolean("CanBeSeen", true)

    /**
     * Should the upwards velocity be set to the `height`-value on next jump?
     *
     * Only true when auto-jumping is currently taking place so that normal jumps
     * are not affected.
     */
    private var adjustNextJump = false

    @Suppress("unused")
    private val movementInputEvent = handler<MovementInputEvent> { event ->
        if (!isActive()) {
            return@handler
        }

        if (!allowsCriticalHit(true)) {
            return@handler
        }

        if (optimizeForCooldown && shouldWaitForJump()) {
            return@handler
        }

        val enemies = world.findEnemies(0f, range)
            .filter { (entity, _) -> !canBeSeen || player.hasLineOfSight(entity) }

        // Change the jump motion only if the jump is a normal jump (small jumps, i.e. honey blocks
        // are not affected) and currently.
        if (enemies.isNotEmpty() && player.onGround()) {
            event.jump = true
            adjustNextJump = true
        }
    }

    @Suppress("unused")
    private val jumpHandler = handler<PlayerJumpEvent> { event ->
        // The `value`-option only changes *normal jumps* with upwards velocity 0.42.
        // Jumps with lower velocity (i.e. from honey blocks) are not affected.
        val isJumpNormal = event.motion == 0.42f

        // Is the jump a normal jump and auto-jumping is enabled.
        if (isJumpNormal && adjustNextJump) {
            event.motion = height
            adjustNextJump = false
        }
    }

    /**
     * Sometimes when the player is almost at the highest point of his jump, the KillAura
     * will try to attack the enemy anyway. To maximise damage, this function is used to determine
     * whether it is worth to wait for the fall.
     */
    fun shouldWaitForCrit(target: Entity, ignoreState: Boolean = false): Boolean {
        if (!isActive() && !ignoreState) {
            return false
        }

        if (player.isFallFlying) {
            return false
        }

        // If general critical conditions are not met, don't wait.
        if (!allowsCriticalHit(ignoreOnGround = true)) {
            return false
        }
        return evaluateCriticalWait(target)
    }

    internal val configuredHeight: Float
        get() = height

    internal val automaticJumpPending: Boolean
        get() = adjustNextJump

    fun shouldWaitForJump(initialMotion: Float = 0.42f): Boolean {
        if (!allowsCriticalHit(true) || !running) {
            return false
        }

        val ticksTillFall = initialMotion / 0.08f
        val nextPossibleCrit = calculateCriticalsJumpTicksUntilNextCrit()

        var ticksTillNextOnGround = FallingPlayer(
            player,
            player.x,
            player.y,
            player.z,
            player.deltaMovement.x,
            player.deltaMovement.y + initialMotion,
            player.deltaMovement.z,
            player.yRot
        ).findCollision((ticksTillFall * 3.0f).toInt())?.tick

        if (ticksTillNextOnGround == null) {
            ticksTillNextOnGround = ticksTillFall.toInt() * 2
        }

        if (ticksTillNextOnGround + ticksTillFall < nextPossibleCrit) {
            return false
        }

        return ticksTillFall + 1.0f < nextPossibleCrit
    }

    private fun isActive(): Boolean {
        if (!ModuleCriticals.running) {
            return false
        }

        // if both module checks are disabled, we can safely say that we are active
        if (!checkKillaura && !checkAutoClicker) {
            return true
        }

        return (ModuleKillAura.running && checkKillaura) ||
            (ModuleAutoClicker.running && checkAutoClicker)
    }

}
