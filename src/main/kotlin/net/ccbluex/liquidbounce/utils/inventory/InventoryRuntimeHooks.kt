/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.utils.inventory

interface InventoryRuntimeProvider {
    val isInventoryOpen: Boolean
    val isInventoryOpenServerSide: Boolean
    val lastClickedSlot: Int
    fun onClickOccurs()
    fun setInventoryOpenServerSide(open: Boolean)
    fun recordClickedSlot(slot: Int)
}

object InventoryRuntimeHooks {
    private val FALLBACK = object : InventoryRuntimeProvider {
        override val isInventoryOpen = false
        override val isInventoryOpenServerSide = false
        override val lastClickedSlot = -1
        override fun onClickOccurs() = Unit
        override fun setInventoryOpenServerSide(open: Boolean) = Unit
        override fun recordClickedSlot(slot: Int) = Unit
    }

    private var provider: InventoryRuntimeProvider = FALLBACK

    @Synchronized
    fun install(provider: InventoryRuntimeProvider) {
        check(this.provider === FALLBACK) { "Inventory runtime provider is already installed" }
        this.provider = provider
    }

    val isInventoryOpen get() = provider.isInventoryOpen
    val isInventoryOpenServerSide get() = provider.isInventoryOpenServerSide
    val lastClickedSlot get() = provider.lastClickedSlot
    fun onClickOccurs() = provider.onClickOccurs()
    fun setInventoryOpenServerSide(open: Boolean) = provider.setInventoryOpenServerSide(open)
    fun recordClickedSlot(slot: Int) = provider.recordClickedSlot(slot)
}
