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

package net.ccbluex.liquidbounce.common.attack

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import java.util.concurrent.atomic.AtomicReference

enum class AcceptedAttackResult {
    APPLIED,
    NOT_APPLIED,
    REJECTED;

    val allowsAttack: Boolean
        get() = this !== REJECTED
}

fun interface AcceptedAttackHandler {
    fun onAcceptedAttack(player: Player, target: Entity): AcceptedAttackResult
}

/**
 * Stable boundary between an accepted vanilla attack and optional feature preparation.
 */
object AcceptedAttackHook {

    private val acceptedAttackCommit = AcceptedAttackCommit<Player, Entity>()

    fun install(handler: AcceptedAttackHandler) {
        acceptedAttackCommit.install(handler::onAcceptedAttack)
    }

    @JvmStatic
    fun commit(player: Player, target: Entity): AcceptedAttackResult =
        acceptedAttackCommit.commit(player, target)
}

/**
 * Installs one owner for accepted-attack preparation and treats an absent owner as a no-op.
 */
internal class AcceptedAttackCommit<P : Any, T : Any> {

    private val handler = AtomicReference<((P, T) -> AcceptedAttackResult)?>(null)

    fun install(handler: (P, T) -> AcceptedAttackResult) {
        check(this.handler.compareAndSet(null, handler)) {
            "An accepted-attack handler is already installed"
        }
    }

    fun commit(player: P, target: T): AcceptedAttackResult =
        handler.get()?.invoke(player, target) ?: AcceptedAttackResult.NOT_APPLIED
}
