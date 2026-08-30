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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.ccbluex.liquidbounce.common.Tagged

/** Input policy for a remote MaceKill attempt. Local accepted mace attacks remain supported. */
internal enum class MaceKillActivationMode(override val tag: String) : Tagged {
    Manual("Manual"),
    HoldAttack("HoldAttack"),
}

/** Selects the single provider used to acquire a remote MaceKill target. */
internal enum class MaceKillTargetSource(override val tag: String) : Tagged {
    Crosshair("Crosshair") {
        override val tagAliases: List<String> = listOf("LookRay")
    },
    Combat("Combat"),
}

internal val DEFAULT_MACE_KILL_ACTIVATION_MODE = MaceKillActivationMode.HoldAttack
internal val DEFAULT_MACE_KILL_TARGET_SOURCE = MaceKillTargetSource.Crosshair

internal fun isMaceKillActivationSatisfied(
    activationMode: MaceKillActivationMode,
    attackHeld: Boolean,
    manualAttackRequested: Boolean,
    inheritedAttackRequested: Boolean = false,
): Boolean = inheritedAttackRequested || when (activationMode) {
    MaceKillActivationMode.Manual -> manualAttackRequested
    MaceKillActivationMode.HoldAttack -> attackHeld
}

internal inline fun <T> selectMaceKillTargetForSource(
    targetSource: MaceKillTargetSource,
    lookRayTarget: () -> T?,
    combatTarget: () -> T?,
): T? = when (targetSource) {
    MaceKillTargetSource.Crosshair -> lookRayTarget()
    MaceKillTargetSource.Combat -> combatTarget()
}

/** KillAura owns both a positive selection and the decision that no MaceKill target is available. */
internal inline fun <T> selectMaceKillDelegatedTarget(
    killAuraAuthoritative: Boolean,
    killAuraTarget: T?,
    localTarget: () -> T?,
): T? = if (killAuraAuthoritative) killAuraTarget else localTarget()

internal fun isMaceKillTargetCandidateEligible(
    isCombatSafe: Boolean,
    isAlive: Boolean,
    isInCurrentWorld: Boolean,
    isWithinRange: Boolean,
    isRejected: Boolean,
    isInWater: Boolean,
): Boolean = isCombatSafe && isAlive && isInCurrentWorld && isWithinRange && !isRejected && !isInWater

internal fun shouldExcludeMaceKillWaterTarget(
    maceKillEnabled: Boolean,
    mainHandMace: Boolean,
    targetInWater: Boolean,
): Boolean = maceKillEnabled && mainHandMace && targetInWater

internal fun isMaceKillEndpointReady(
    holdingMace: Boolean,
    bodySpaceClear: Boolean,
    attackRayClear: Boolean,
    cooldownReady: Boolean,
    usableFallHeight: Int,
): Boolean = holdingMace && bodySpaceClear && attackRayClear && cooldownReady && usableFallHeight > 0

internal fun shouldMaceKillLookRayIgnoreTerrain(
    packetMovement: Boolean,
    aStarRouting: Boolean,
    instantRouting: Boolean,
    clipReachResearch: Boolean,
): Boolean = clipReachResearch || packetMovement && (aStarRouting || instantRouting)
