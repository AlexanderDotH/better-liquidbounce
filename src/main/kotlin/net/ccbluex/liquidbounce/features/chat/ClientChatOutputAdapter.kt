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

package net.ccbluex.liquidbounce.features.chat

import net.ccbluex.liquidbounce.common.chat.ClientChatOutput
import net.ccbluex.liquidbounce.common.chat.ClientChatSink

object ClientChatOutputAdapter : ClientChatSink {
    fun install() = ClientChatOutput.install(this)

    override fun publish(text: net.minecraft.network.chat.Component) = chat(text)
}
