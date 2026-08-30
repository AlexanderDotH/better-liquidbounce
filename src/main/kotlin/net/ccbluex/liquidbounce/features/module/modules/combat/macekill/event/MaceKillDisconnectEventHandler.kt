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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event


import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.MaceKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration.clearRuntime
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.event.events.DisconnectEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotTerminal

internal fun MaceKillModuleState.registerMaceKillDisconnectHandler() {
    handler<DisconnectEvent> {
        clearRuntime(MaceKillFightBotTerminal.Disconnect)
    }

}
