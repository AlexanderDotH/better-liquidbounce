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
package net.ccbluex.liquidbounce.features.module.modules.misc.packetlogger

import net.ccbluex.liquidbounce.utils.kotlin.isNotRoot
import net.minecraft.network.protocol.Packet
import java.lang.reflect.Modifier
import java.lang.reflect.Type

@JvmRecord
internal data class PacketField(val name: String, val type: Type, val value: Any?)

internal fun collectPacketFields(clazz: Class<out Packet<*>>, packet: Packet<*>): List<PacketField> {
    val fields = mutableListOf<PacketField>()
    var currentClass: Class<*>? = clazz

    while (currentClass.isNotRoot()) {
        currentClass.declaredFields.forEach { field ->
            if (Modifier.isStatic(field.modifiers)) return@forEach
            field.isAccessible = true
            val value = try {
                field.get(packet)?.toString()
            } catch (@Suppress("SwallowedException") _: IllegalAccessException) {
                "null"
            }
            fields += PacketField(field.name, field.genericType, value)
        }
        currentClass = currentClass.superclass
    }

    return fields
}
