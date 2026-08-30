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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*

import net.ccbluex.liquidbounce.common.attack.AcceptedAttackHook
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.concurrent.atomic.AtomicReference

enum class MaceKillAttackResult {
    APPLIED,
    NOT_APPLIED,
    REJECTED;

    val allowsAttack: Boolean
        get() = this !== REJECTED
}

fun interface MaceKillAcceptedAttackHandler {
    fun onAcceptedAttack(player: Player, target: Entity): MaceKillAttackResult
}

/**
 * The single boundary between a validated attack and MaceKill's pre-attack movement spoof.
 */
object MaceKillAttackHook {

    fun install(handler: MaceKillAcceptedAttackHandler) {
        AcceptedAttackHook.install { player, target ->
            handler.onAcceptedAttack(player, target).toAcceptedAttackResult()
        }
    }

    @JvmStatic
    fun commit(player: Player, target: Entity): MaceKillAttackResult =
        AcceptedAttackHook.commit(player, target).toMaceKillAttackResult()

}

private fun MaceKillAttackResult.toAcceptedAttackResult(): AcceptedAttackResult = when (this) {
    MaceKillAttackResult.APPLIED -> AcceptedAttackResult.APPLIED
    MaceKillAttackResult.NOT_APPLIED -> AcceptedAttackResult.NOT_APPLIED
    MaceKillAttackResult.REJECTED -> AcceptedAttackResult.REJECTED
}

private fun AcceptedAttackResult.toMaceKillAttackResult(): MaceKillAttackResult = when (this) {
    AcceptedAttackResult.APPLIED -> MaceKillAttackResult.APPLIED
    AcceptedAttackResult.NOT_APPLIED -> MaceKillAttackResult.NOT_APPLIED
    AcceptedAttackResult.REJECTED -> MaceKillAttackResult.REJECTED
}

/**
 * Installs one owner for the accepted-attack boundary and leaves absent handlers as a no-op.
 */
internal class MaceKillAcceptedAttackCommit<P : Any, T : Any> {

    private val handler = AtomicReference<((P, T) -> MaceKillAttackResult)?>(null)

    fun install(handler: (P, T) -> MaceKillAttackResult) {
        check(this.handler.compareAndSet(null, handler)) {
            "A MaceKill accepted-attack handler is already installed"
        }
    }

    fun commit(player: P, target: T): MaceKillAttackResult =
        handler.get()?.invoke(player, target) ?: MaceKillAttackResult.NOT_APPLIED

}
