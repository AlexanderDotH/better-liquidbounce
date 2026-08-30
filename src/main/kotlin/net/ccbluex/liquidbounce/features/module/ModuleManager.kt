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
package net.ccbluex.liquidbounce.features.module

import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet
import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.features.autoconfig.AutoConfig
import net.ccbluex.liquidbounce.features.autoconfig.contract.AutoConfigModuleBridge
import net.ccbluex.liquidbounce.config.types.VALUE_NAME_ORDER
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.events.KeyboardKeyEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.tickUntil
import net.ccbluex.liquidbounce.annotations.ScriptApiRequired
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.logger

private val modules = ObjectRBTreeSet<ClientModule>(VALUE_NAME_ORDER)

/**
 * A fairly simple module manager
 */
object ModuleManager : EventListener, Collection<ClientModule> by modules {

    val modulesConfig = ConfigSystem.root("modules", modules)

    init {
        AutoConfigModuleBridge.install(modulesConfig, this)
    }

    private val bindController = ModuleBindController(modules)

    @Suppress("unused")
    private val keyboardKeyHandler = handler<KeyboardKeyEvent>(handler = bindController::handleKeyboard)

    @Suppress("unused")
    private val mouseButtonHandler = handler<MouseButtonEvent>(handler = bindController::handleMouse)

    /**
     * Handles world change and enables modules that are not enabled yet
     */
    @Suppress("unused")
    private val handleWorldChange = sequenceHandler<WorldChangeEvent> { event ->
        // Delayed start handling
        if (event.world != null) {
            tickUntil { inGame }
            AutoConfig.withLoading {
                for (module in modules) {
                    if (!module.enabled || module.calledSinceStartup) continue

                    try {
                        module.calledSinceStartup = true
                        // inGame is false here, so use onToggle0
                        module.onToggled(true)
                    } catch (e: Exception) {
                        logger.error("Failed to enable module ${module.name}", e)
                    }
                }
            }
        }

        // Store modules configuration after world change, happens on disconnect as well
        ConfigSystem.store(modulesConfig)
    }

    /**
     * Handles disconnect and if [ClientModule.disableOnQuit] is true disables module
     */
    @Suppress("unused")
    private val handleDisconnect = handler<DisconnectEvent> {
        for (module in modules) {
            if (module.disableOnQuit) {
                try {
                    module.enabled = false
                } catch (e: Exception) {
                    logger.error("Failed to disable module ${module.name}", e)
                }
            }
        }
    }

    /** Register inbuilt client modules in their historical order. */
    fun registerInbuilt(builtinModules: Iterable<ClientModule>) {
        builtinModules.forEach { module ->
            addModule(module)
            module.walkKeyPath()
            module.verifyFallbackDescription()
        }
    }

    fun addModule(module: ClientModule) {
        if (!modules.add(module)) {
            error("Module '${module.name}' is already registered.")
        }
        module.walkInit()
        module.onRegistration()
    }

    fun removeModule(module: ClientModule) {
        if (!modules.remove(module)) {
            error("Module '${module.name}' is not registered.")
        }
        if (module.enabled) {
            module.enabled = false
        }
        module.unregister()
    }

    fun clear() {
        modules.clear()
    }

    /**
     * This is being used by UltralightJS for the implementation of the ClickGUI. DO NOT REMOVE!
     */
    @JvmName("getCategories")
    @ScriptApiRequired
    fun getCategories() = ModuleCategories.entries.mapToArray { it.tag }

    @JvmName("getModules")
    @ScriptApiRequired
    fun getModules(): Collection<ClientModule> = modules

    @JvmName("getModuleByName")
    @ScriptApiRequired
    fun getModuleByName(module: String) = findModuleByNameOrAlias(module)

    operator fun get(moduleName: String) = findModuleByNameOrAlias(moduleName)

    private fun findModuleByNameOrAlias(moduleName: String) = findByExactNameOrAlias(
        values = modules,
        requestedName = moduleName,
        nameOf = ClientModule::name,
        aliasesOf = ClientModule::aliases,
    )
}
