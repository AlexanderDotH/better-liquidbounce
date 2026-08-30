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
package net.ccbluex.liquidbounce.features.account

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AccountManagerRemovalResultEvent

interface AccountCollectionOperations {
    fun favoriteAccount(id: Int)
    fun unfavoriteAccount(id: Int)
    fun swapAccounts(index1: Int, index2: Int)
    fun orderAccounts(order: List<Int>)
    fun removeAccount(id: Int): MinecraftAccount
}

internal object AccountCollectionDelegate : AccountCollectionOperations {

    override fun favoriteAccount(id: Int) {
        val account = AccountManager.accounts.getOrNull(id) ?: error("Account not found!")
        account.favorite = true
        ConfigSystem.store(AccountManager)
    }

    override fun unfavoriteAccount(id: Int) {
        val account = AccountManager.accounts.getOrNull(id) ?: error("Account not found!")
        account.favorite = false
        ConfigSystem.store(AccountManager)
    }

    override fun swapAccounts(index1: Int, index2: Int) {
        val account1 = AccountManager.accounts.getOrNull(index1) ?: error("Account not found!")
        val account2 = AccountManager.accounts.getOrNull(index2) ?: error("Account not found!")
        AccountManager.accounts[index1] = account2
        AccountManager.accounts[index2] = account1
        ConfigSystem.store(AccountManager)
    }

    override fun orderAccounts(order: List<Int>) {
        order.map { index -> AccountManager.accounts[index] }
            .forEachIndexed { index, account ->
                AccountManager.accounts[index] = account
            }

        ConfigSystem.store(AccountManager)
    }

    override fun removeAccount(id: Int): MinecraftAccount {
        val account = AccountManager.accounts.removeAt(id).apply { ConfigSystem.store(AccountManager) }
        EventManager.callEvent(AccountManagerRemovalResultEvent(account.username))
        return account
    }
}
