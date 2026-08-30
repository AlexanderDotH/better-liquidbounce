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
package net.ccbluex.liquidbounce.features.combat.contract

import net.ccbluex.liquidbounce.common.interop.PlayerDataPayload
import net.minecraft.world.entity.player.Player

fun interface CombatTargetSnapshotFactory {
    fun create(player: Player): PlayerDataPayload
}

object CombatTargetSnapshots {

    @Volatile
    private var factory: CombatTargetSnapshotFactory? = null

    @Synchronized
    fun install(factory: CombatTargetSnapshotFactory) {
        check(this.factory == null) { "Combat target snapshot adapter is already installed" }
        this.factory = factory
    }

    fun fromPlayer(player: Player): PlayerDataPayload = checkNotNull(factory) {
        "The combat target snapshot adapter has not been installed"
    }.create(player)

    @Synchronized
    internal fun <T> withFactoryForTest(factory: CombatTargetSnapshotFactory?, block: () -> T): T {
        val previous = this.factory
        this.factory = factory
        return try {
            block()
        } finally {
            this.factory = previous
        }
    }
}
