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
package net.ccbluex.liquidbounce.features.module.modules.player.autoshop

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.event.tickConditional
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.AutoShopConfig.loadAutoShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.NormalPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.purchasemode.QuickPurchaseMode
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopConfig
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.serializable.ShopElement
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleDebug
import net.ccbluex.liquidbounce.utils.kotlin.Minecraft
import net.ccbluex.liquidbounce.utils.kotlin.subList
import net.ccbluex.liquidbounce.utils.text.stripMinecraftColorCodes
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import kotlin.coroutines.CoroutineContext

private object DeferredMinecraftDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        Dispatchers.Minecraft.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Dispatchers.Minecraft.dispatch(context, block)
    }
}

/**
 * Server-shop AutoShop mode.
 *
 * Automatically buys configured items in a BedWars shop.
 */
object AutoShopServerMode : Mode("ServerShop") {

    override val parent: ModeValueGroup<*>
        get() = ModuleAutoShop.modes

    private val shopConfig by enumChoice("Config", ShopConfigPreset.PIKA_NETWORK).onChanged {
        loadAutoShopConfig(it)
    }
    private val startDelay by intRange("StartDelay", 1..2, 0..10, "ticks")
    val purchaseMode = modes(
        this,
        "PurchaseMode",
        NormalPurchaseMode,
        arrayOf(NormalPurchaseMode, QuickPurchaseMode),
    )
    private val extraCategorySwitchDelay by intRange(
        "ExtraCategorySwitchDelay",
        3..4,
        0..10,
        "ticks",
    )
    private val autoClose by boolean("AutoClose", true)

    private val session = ServerShopSessionState()
    private val planner = PurchaseSimulationPlanner()
    private val clickExecutor = CategoryClickExecutor(
        state = session,
        planner = planner,
        isShopOpen = ::isShopOpen,
        isNormalPurchaseMode = { purchaseMode.activeMode == NormalPurchaseMode },
        categorySwitchDelay = { extraCategorySwitchDelay.random() },
    )

    var currentConfig = ShopConfig.Empty
        private set
    private var hasInstalledConfig = false

    init {
        loadAutoShopConfig(shopConfig)
    }

    override fun disable() {
        reset()
    }

    internal fun installConfig(config: ShopConfig) {
        currentConfig = config
        if (hasInstalledConfig) {
            reset()
        }
        hasInstalledConfig = true
    }

    @Suppress("unused")
    private val repeatable = tickHandler(DeferredMinecraftDispatcher) {
        if (!isShopOpen()) {
            return@tickHandler
        }
        if (ModuleDebug.running) {
            session.beginDebugSession(System.currentTimeMillis())
        }
        waitBeforeFirstClick()
        if (!isShopOpen()) {
            reset()
            return@tickHandler
        }

        for (index in currentConfig.elements.indices) {
            val element = currentConfig.elements[index]
            val remainingElements = currentConfig.elements.subList(index)
            if (!buyElementUntilSatisfied(element, remainingElements)) {
                reset()
                return@tickHandler
            }
        }

        if (session.waitedBeforeFirstClick && autoClose && session.canAutoClose) {
            player.closeContainer()
        }
        reset()
    }

    private suspend fun waitBeforeFirstClick() {
        if (session.waitedBeforeFirstClick) {
            return
        }
        tickConditional(startDelay.random()) { !isShopOpen() }
        session.markInitialDelayComplete()
    }

    private suspend fun buyElementUntilSatisfied(
        element: ShopElement,
        remainingElements: List<ShopElement>,
    ): Boolean {
        while (planner.checkElement(element, remainingElements) != null) {
            session.markPurchaseStarted()
            clickExecutor.execute(remainingElements)
            if (!isShopOpen()) {
                return false
            }
        }
        return true
    }

    private fun isShopOpen(): Boolean {
        val screen = mc.gui.screen() as? ContainerScreen ?: return false
        val title = screen.title.string.stripMinecraftColorCodes()
        return currentConfig.traderTitles.any { title.contains(it, ignoreCase = true) }
    }

    private fun reset() {
        session.reset(
            initialCategorySlot = currentConfig.initialCategorySlot,
            debug = ModuleDebug.running,
            now = System::currentTimeMillis,
        )?.let { summary ->
            chat("[AutoShop] Time elapsed: ${summary.elapsedMilliseconds} ms")
            chat("[AutoShop] Clicked on the following slots: ${summary.clickedSlots}")
        }
        AutoShopInventoryManager.clearPendingItems()
    }
}
