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
package net.ccbluex.liquidbounce.features.module.modules.combat

/** Cleanup required when KillAura shuts down while SpearKill may still own movement or item use. */
internal enum class SpearKillKillAuraReleaseAction {
    NONE,
    RELEASE_INHERITED_USE,
    CANCEL_INHERITED_PREPARATION,
    CANCEL_INHERITED_ROUTE,
    DEACTIVATE,
    DEACTIVATE_AND_RETURN,
}

internal fun resolveSpearKillKillAuraReleaseAction(
    spearKillEnabled: Boolean,
    killAuraOwnsAttempt: Boolean,
    routeActive: Boolean,
    killAuraPreparationActive: Boolean,
    inheritedUseActive: Boolean,
): SpearKillKillAuraReleaseAction = when {
    spearKillEnabled && routeActive -> SpearKillKillAuraReleaseAction.DEACTIVATE_AND_RETURN
    spearKillEnabled -> SpearKillKillAuraReleaseAction.DEACTIVATE
    killAuraOwnsAttempt -> SpearKillKillAuraReleaseAction.CANCEL_INHERITED_ROUTE
    killAuraPreparationActive -> SpearKillKillAuraReleaseAction.CANCEL_INHERITED_PREPARATION
    inheritedUseActive -> SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE
    else -> SpearKillKillAuraReleaseAction.NONE
}
