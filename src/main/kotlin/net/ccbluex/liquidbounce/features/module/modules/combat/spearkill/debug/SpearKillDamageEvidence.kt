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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug

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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

/** One target damage event correlated with a predicted SpearKill hit. */
internal data class SpearKillDamageEvidence(
    val targetEntityId: Int,
    val predictedHitTick: Int,
    val observedTick: Int,
)

/**
 * Correlates one incoming damage event with one predicted SpearKill hit.
 *
 * A `null` result means only that no matching evidence was observed. It never confirms a miss.
 */
internal class SpearKillDamageEvidenceTracker(
    private val windowTicks: Int = SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
) {

    private var pendingEvidence: PendingEvidence? = null

    init {
        require(windowTicks >= 0) { "Damage evidence window must not be negative" }
    }

    val isArmed: Boolean
        get() = pendingEvidence != null

    /** Replaces any older pending target correlation. */
    fun arm(
        targetEntityId: Int,
        predictedHitTick: Int,
        windowTicks: Int = this.windowTicks,
    ) {
        require(windowTicks >= 0) { "Damage evidence window must not be negative" }
        pendingEvidence = PendingEvidence(targetEntityId, predictedHitTick, windowTicks)
    }

    /**
     * Returns evidence only for the armed target in its inclusive hit window.
     *
     * An event after the window expires the pending correlation. An event outside the window or
     * for another target remains inconclusive, so the correlation stays armed.
     */
    fun observe(entityId: Int, observedTick: Int): SpearKillDamageEvidence? {
        val pending = pendingEvidence ?: return null
        if (isSpearKillDamageEvidenceExpired(pending.predictedHitTick, observedTick, pending.windowTicks)) {
            clear()
            return null
        }
        if (pending.targetEntityId != entityId ||
            !isSpearKillDamageEvidenceWithinWindow(
                pending.predictedHitTick,
                observedTick,
                pending.windowTicks,
            )
        ) {
            return null
        }

        clear()
        return SpearKillDamageEvidence(pending.targetEntityId, pending.predictedHitTick, observedTick)
    }

    /** Clears the correlation once a game tick has moved past its inclusive window. */
    fun expire(currentTick: Int): Boolean {
        val pending = pendingEvidence ?: return false
        if (!isSpearKillDamageEvidenceExpired(pending.predictedHitTick, currentTick, pending.windowTicks)) {
            return false
        }

        clear()
        return true
    }

    fun clear() {
        pendingEvidence = null
    }

    private data class PendingEvidence(
        val targetEntityId: Int,
        val predictedHitTick: Int,
        val windowTicks: Int,
    )
}

/** Inclusive tolerance window centered on a predicted hit tick. */
internal fun spearKillDamageEvidenceWindow(
    predictedHitTick: Int,
    windowTicks: Int = SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
): LongRange {
    require(windowTicks >= 0) { "Damage evidence window must not be negative" }

    val predictedTick = predictedHitTick.toLong()
    return predictedTick - windowTicks..predictedTick + windowTicks
}

internal fun isSpearKillDamageEvidenceWithinWindow(
    predictedHitTick: Int,
    observedTick: Int,
    windowTicks: Int = SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS,
): Boolean = observedTick.toLong() in spearKillDamageEvidenceWindow(predictedHitTick, windowTicks)

private fun isSpearKillDamageEvidenceExpired(
    predictedHitTick: Int,
    currentTick: Int,
    windowTicks: Int,
): Boolean = currentTick.toLong() > spearKillDamageEvidenceWindow(predictedHitTick, windowTicks).last

internal const val SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS = 2
