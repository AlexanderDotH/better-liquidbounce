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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.common.Tagged
import net.minecraft.world.entity.LivingEntity

internal enum class FightBotTargetMode(override val tag: String) : Tagged {
    Nearest("Nearest"),
    Named("Named"),
}

internal enum class FightBotSpearAutomation(override val tag: String) : Tagged {
    Off("Off"),
    HeldSpear("HeldSpear"),
    HeldOrHotbar("HeldOrHotbar"),
}

internal enum class FightBotMaceAutomation(override val tag: String) : Tagged {
    Off("Off"),
    HeldMace("HeldMace"),
    HeldOrHotbar("HeldOrHotbar"),
}

internal enum class FightBotAutoAction(override val tag: String) : Tagged {
    JUMP("Jump"),
    SWIM("Swim"),
    SPRINT("Sprint"),
}

internal sealed interface FightBotTargetHandoff {
    data object Inactive : FightBotTargetHandoff
    data object Idle : FightBotTargetHandoff
    data class Locked(val target: LivingEntity) : FightBotTargetHandoff
}

internal enum class FightBotHandoffState {
    Inactive,
    Idle,
    Locked,
}

internal val FightBotTargetHandoff.state: FightBotHandoffState
    get() = when (this) {
        FightBotTargetHandoff.Inactive -> FightBotHandoffState.Inactive
        FightBotTargetHandoff.Idle -> FightBotHandoffState.Idle
        is FightBotTargetHandoff.Locked -> FightBotHandoffState.Locked
    }

internal val FightBotTargetHandoff.lockedTarget: LivingEntity?
    get() = (this as? FightBotTargetHandoff.Locked)?.target

internal data class FightBotTargetSafety(
    val combatSafe: Boolean = true,
    val self: Boolean = false,
    val alive: Boolean = true,
    val removed: Boolean = false,
    val withinRange: Boolean = true,
    val withinFov: Boolean = true,
    val hurtTimeAccepted: Boolean = true,
    val visible: Boolean = true,
    val supported: Boolean = true,
) {
    val isEligible: Boolean
        get() = combatSafe && !self && alive && !removed && withinRange && withinFov &&
            hurtTimeAccepted && visible && supported
}

internal fun <T> selectFightBotTarget(
    mode: FightBotTargetMode,
    configuredName: String,
    candidates: Iterable<T>,
    nameOf: (T) -> String?,
    distanceOf: (T) -> Double,
    isEligible: (T) -> Boolean,
): T? = when (mode) {
    FightBotTargetMode.Nearest -> candidates.asSequence()
        .filter(isEligible)
        .minByOrNull(distanceOf)
    FightBotTargetMode.Named -> selectConfiguredFightBotTarget(
        username = configuredName,
        candidates = candidates,
        usernameOf = nameOf,
        isEligible = isEligible,
    )
}

internal fun <T> selectConfiguredFightBotTarget(
    username: String,
    candidates: Iterable<T>,
    usernameOf: (T) -> String?,
    isEligible: (T) -> Boolean,
): T? {
    val normalizedUsername = username.trim()
    if (normalizedUsername.isEmpty()) return null

    return candidates.firstOrNull { candidate ->
        usernameOf(candidate)?.equals(normalizedUsername, ignoreCase = true) == true && isEligible(candidate)
    }
}

internal fun <T> selectKillAuraTargetForFightBot(
    handoff: FightBotHandoffState,
    lockedTarget: T?,
    trackedTarget: T?,
    crosshairTarget: T?,
): T? = when (handoff) {
    FightBotHandoffState.Inactive -> crosshairTarget ?: trackedTarget
    FightBotHandoffState.Idle -> null
    FightBotHandoffState.Locked -> lockedTarget
}

internal data class FightBotKillAuraLease(
    val ownsKillAura: Boolean,
    val enableKillAura: Boolean,
    val halted: Boolean = false,
) {
    val shouldDisableKillAuraOnRelease: Boolean
        get() = ownsKillAura

    fun onKillAuraDisabled() = copy(enableKillAura = false, halted = true)

    fun isOperational(killAuraEnabled: Boolean) = killAuraEnabled && !halted

    companion object {
        fun start(autoEnable: Boolean, killAuraEnabled: Boolean): FightBotKillAuraLease {
            val shouldEnable = autoEnable && !killAuraEnabled
            return FightBotKillAuraLease(
                ownsKillAura = shouldEnable,
                enableKillAura = shouldEnable,
                halted = !killAuraEnabled && !shouldEnable,
            )
        }
    }
}
