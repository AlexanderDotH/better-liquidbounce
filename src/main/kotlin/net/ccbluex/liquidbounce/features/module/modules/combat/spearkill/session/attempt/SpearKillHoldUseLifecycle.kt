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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
/** Remembers which target an uninterrupted physical HoldUse gesture most recently launched at. */
internal fun <T : Any> nextSpearKillHoldUseLaunchTarget(
    activationMode: SpearKillActivationMode,
    holdingSpear: Boolean,
    useInputHeld: Boolean,
    currentTarget: T?,
    launchedTarget: T?,
    launchStarted: Boolean,
): T? {
    if (activationMode != SpearKillActivationMode.HoldUse || !holdingSpear || !useInputHeld) return null
    if (!launchStarted) return currentTarget

    return launchedTarget ?: currentTarget
}

/**
 * After the first manual launch, only a genuinely different cursor target may re-arm the same hold.
 * Automatic owners retain their configured selector and independent scheduling lifecycle.
 */
internal fun <T : Any> selectSpearKillHoldUseLaunchTarget(
    activationMode: SpearKillActivationMode,
    useInputHeld: Boolean,
    automaticRequest: Boolean,
    previousLaunchTarget: T?,
    cursorTarget: T?,
    configuredTarget: T?,
): T? {
    if (!isSpearKillHoldUseCursorRetargetRequested(
            activationMode,
            useInputHeld,
            automaticRequest,
            previousLaunchTarget,
        )
    ) {
        return configuredTarget
    }

    return cursorTarget?.takeUnless { it === previousLaunchTarget }
}

internal fun <T : Any> isSpearKillHoldUseCursorRetargetRequested(
    activationMode: SpearKillActivationMode,
    useInputHeld: Boolean,
    automaticRequest: Boolean,
    previousLaunchTarget: T?,
): Boolean = activationMode == SpearKillActivationMode.HoldUse &&
    useInputHeld && !automaticRequest && previousLaunchTarget != null

/** Automatic owners may keep scheduling routes; manual HoldUse requires a new target identity. */
internal fun <T : Any> isSpearKillLaunchActivationSatisfied(
    activationMode: SpearKillActivationMode,
    activationRequested: Boolean,
    previousLaunchTarget: T?,
    launchTarget: T?,
    automaticRequest: Boolean,
): Boolean = activationRequested && (
    automaticRequest ||
        activationMode != SpearKillActivationMode.HoldUse ||
        previousLaunchTarget == null ||
        launchTarget != null && launchTarget !== previousLaunchTarget
    )
