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
 */

package net.ccbluex.liquidbounce.config.types

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.ccbluex.liquidbounce.utils.input.inputByName

fun Value<InputBind>.bind(name: String) = set(get().copy(boundKey = inputByName(name)))

fun Value<InputBind>.bind(
    key: InputConstants.Key,
    action: InputBind.BindAction,
    modifiers: Set<InputBind.Modifier>,
) = set(get().copy(boundKey = key, action = action, modifiers = modifiers))

fun Value<InputBind>.unbind() = set(InputBind.UNBOUND)
