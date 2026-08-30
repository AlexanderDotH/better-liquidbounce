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

@file:JvmName("WorldExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.world

import net.minecraft.world.level.Level
import java.util.concurrent.atomic.AtomicInteger

private val localEntityIdGenerator = AtomicInteger(-1)

/**
 * Allocates a unique negative entity ID for a locally spawned entity.
 *
 * Server entity IDs are always positive, and [Level.getNextEntityId] returns 0 on the client,
 * which [net.minecraft.client.multiplayer.ClientLevel.addEntity] rejects by throwing
 * `Tried to access entity ID before ID assignment`.
 */
fun Level.nextLocalEntityId(): Int = localEntityIdGenerator.getAndDecrement()
