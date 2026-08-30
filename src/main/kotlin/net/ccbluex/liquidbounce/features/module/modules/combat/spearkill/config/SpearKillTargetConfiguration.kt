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

@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config


import net.ccbluex.liquidbounce.common.Tagged

/**
 * Input that activates SpearKill after a target has been selected.
 *
 * [Manual] preserves the historical attack-key/click request. [HoldUse] lets the caller use a
 * held-use signal as the activation gate instead.
 */
internal enum class SpearKillActivationMode(override val tag: String) : Tagged {
    Manual("Manual"),
    HoldUse("HoldUse"),
}

/** Describes where SpearKill acquires a candidate without coupling the policy to a module runtime. */
internal enum class SpearKillTargetSource(override val tag: String) : Tagged {
    Crosshair("Crosshair") {
        override val tagAliases: List<String> = listOf("LookRay")
    },
    Combat("Combat"),
}

/** Explicit attack intent for configurations that omit the setting. */
internal val DEFAULT_SPEAR_KILL_ACTIVATION_MODE = SpearKillActivationMode.Manual

/** Crosshair target acquisition for configurations that omit the setting. */
internal val DEFAULT_SPEAR_KILL_TARGET_SOURCE = SpearKillTargetSource.Crosshair

/** Whether the legacy attack-key/click request remains part of the activation gate. */
internal fun requiresSpearKillAttackRequest(activationMode: SpearKillActivationMode): Boolean =
    activationMode == SpearKillActivationMode.Manual

/**
 * Keeps one Manual click armed while the same spear-use hold is charging or refreshing.
 *
 * This deliberately does not latch HoldUse: that mode has its own one-launch-per-hold lifecycle and
 * must not inherit a stale attack click.
 */
internal fun nextSpearKillManualAttackRequestLatch(
    activationMode: SpearKillActivationMode,
    holdingSpear: Boolean,
    isUsingSpear: Boolean,
    useInputHeld: Boolean,
    wasLatched: Boolean,
    attackPressed: Boolean,
): Boolean = activationMode == SpearKillActivationMode.Manual &&
    holdingSpear &&
    isUsingSpear &&
    useInputHeld &&
    (wasLatched || attackPressed)

/** Resolves the user intent gate without coupling the policy to Minecraft key bindings. */
internal fun isSpearKillActivationSatisfied(
    activationMode: SpearKillActivationMode,
    attackRequested: Boolean,
    useKeyDown: Boolean,
    inheritedKillAuraRequest: Boolean = false,
): Boolean = inheritedKillAuraRequest || when (activationMode) {
    SpearKillActivationMode.Manual -> attackRequested
    SpearKillActivationMode.HoldUse -> useKeyDown
}

/** Final same-tick launch gate after charge acceleration and target selection have run. */
internal fun shouldStartSpearKillAttempt(
    attackActive: Boolean,
    activationSatisfied: Boolean,
    hasTarget: Boolean,
    ticksUsingItem: Int,
    delayTicks: Int,
    damageUseDuration: Int,
): Boolean = !attackActive && activationSatisfied && hasTarget &&
    damageUseDuration >= delayTicks && ticksUsingItem >= delayTicks

/**
 * Candidate acquisition deliberately starts before charge and activation are complete. Otherwise
 * KillAura cannot discover a target that exists only inside SpearKill's longer configured range.
 */
internal fun isSpearKillKillAuraAcquisitionAvailable(
    moduleEnabled: Boolean,
    moduleRunning: Boolean,
    delegationEnabled: Boolean,
    holdingSpear: Boolean,
    routeBlocked: Boolean,
): Boolean = moduleEnabled && moduleRunning && delegationEnabled && holdingSpear && !routeBlocked

/** Execution remains fail-closed after the wider acquisition range has supplied a candidate. */
internal fun isSpearKillKillAuraAttackArmed(
    acquisitionAvailable: Boolean,
    usingSpear: Boolean,
    activationRequested: Boolean,
    hasKineticWeapon: Boolean,
): Boolean = acquisitionAvailable && usingSpear && activationRequested && hasKineticWeapon

/** Whether the source supplies candidates independently of the player's current look ray. */
internal fun isSpearKillTargetSourceAutomatic(targetSource: SpearKillTargetSource): Boolean = when (targetSource) {
    SpearKillTargetSource.Crosshair -> false
    SpearKillTargetSource.Combat -> true
}

/** Selects exactly one target provider; inactive providers are never evaluated. */
internal inline fun <T> selectSpearKillTargetForSource(
    targetSource: SpearKillTargetSource,
    lookRayTarget: () -> T?,
    combatTarget: () -> T?,
): T? = when (targetSource) {
    SpearKillTargetSource.Crosshair -> lookRayTarget()
    SpearKillTargetSource.Combat -> combatTarget()
}

/** Once movement is committed, a new selector result cannot replace the locked target. */
internal fun <T> preferLockedSpearKillTarget(lockedTarget: T?, selectedTarget: T?): T? =
    lockedTarget ?: selectedTarget

/** A transient route retry owns the same target lock as an already committed movement route. */
internal fun <T> activeSpearKillTargetLock(
    lockedTarget: T?,
    routeActive: Boolean,
    routePreparationActive: Boolean,
): T? = lockedTarget.takeIf { routeActive || routePreparationActive }

/**
 * Shared fail-closed acceptance boundary for every target source.
 *
 * [isAlive] is intentionally caller-owned liveness: entity callers should include removal in that
 * value (for example, `entity.isAlive && !entity.isRemoved`) before crossing this pure boundary.
 */
internal fun isSpearKillTargetCandidateEligible(
    isCombatSafe: Boolean,
    isAlive: Boolean,
    isInCurrentWorld: Boolean,
    isWithinRange: Boolean,
    isRejected: Boolean,
): Boolean = isCombatSafe && isAlive && isInCurrentWorld && isWithinRange && !isRejected
