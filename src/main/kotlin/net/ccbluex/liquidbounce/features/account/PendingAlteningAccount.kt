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
package net.ccbluex.liquidbounce.features.account

import net.ccbluex.liquidbounce.api.thirdparty.TheAlteningGeneratedAccount

private const val PENDING_ALTENING_ACCOUNT_NAME = "TheAltening"

internal fun createPendingAlteningAccount(generatedAccount: TheAlteningGeneratedAccount): AlteningAccount {
    val username = generatedAccount.username
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: PENDING_ALTENING_ACCOUNT_NAME

    return pendingAlteningAccount(generatedAccount.token, username)
}

internal fun pendingAlteningAccount(accountToken: String, username: String) =
    AlteningAccount.pending(accountToken, username)
