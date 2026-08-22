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

    private val acceptedAttackCommit = MaceKillAcceptedAttackCommit<Player, Entity>()

    fun install(handler: MaceKillAcceptedAttackHandler) {
        acceptedAttackCommit.install(handler::onAcceptedAttack)
    }

    @JvmStatic
    fun commit(player: Player, target: Entity): MaceKillAttackResult =
        acceptedAttackCommit.commit(player, target)

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
