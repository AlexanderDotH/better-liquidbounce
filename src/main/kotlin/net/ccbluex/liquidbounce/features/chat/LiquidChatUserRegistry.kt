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

import net.ccbluex.liquidbounce.features.chat.packet.AxoUser
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal class LiquidChatUserRegistry {

    private val rememberedUsers = AtomicReference<Map<UUID, AxoUser>>(emptyMap())

    fun remember(user: AxoUser) {
        rememberedUsers.updateAndGet { current ->
            if (current[user.uuid] == user) current else current + (user.uuid to user)
        }
    }

    fun contains(uuid: UUID): Boolean = uuid in rememberedUsers.get()

    fun user(uuid: UUID): AxoUser? = rememberedUsers.get()[uuid]

    fun users(): Collection<AxoUser> = rememberedUsers.get().values

    fun clear() = rememberedUsers.set(emptyMap())
}

internal object LiquidChatUsers {

    private val registry = LiquidChatUserRegistry()

    fun remember(user: AxoUser) = registry.remember(user)

    fun contains(uuid: UUID) = registry.contains(uuid)

    fun clear() = registry.clear()
}
