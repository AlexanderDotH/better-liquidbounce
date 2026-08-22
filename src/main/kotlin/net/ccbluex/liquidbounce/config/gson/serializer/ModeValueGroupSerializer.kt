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
package net.ccbluex.liquidbounce.config.gson.serializer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import java.lang.reflect.Type

class ModeValueGroupSerializer private constructor(
    private val withValueType: Boolean,
    private val includePrivate: Boolean,
    private val includeNotAnOption: Boolean
) : JsonSerializer<ModeValueGroup<Mode>> {

    override fun serialize(
        src: ModeValueGroup<Mode>, typeOfSrc: Type, context: JsonSerializationContext
    ): JsonElement {
        val obj = JsonObject()

        obj.addProperty("name", src.name)
        obj.addProperty("active", src.activeMode.tag)
        obj.add(
            "value",
            context.serialize(
                src.inner
                    .filter { includeNotAnOption || !it.notAnOption }
                    .filter { includePrivate || it.checkIfInclude() }
            )
        )

        val choices = JsonObject()

        for (choice in src.modes) {
            if (includePrivate || choice.checkIfInclude()) {
                val serializedChoice = context.serialize(choice).asJsonObject

                if (withValueType) {
                    serializedChoice.addInteropMetadata(choice)
                }

                choices.add(choice.name, serializedChoice)
            }
        }

        obj.add("choices", choices)
        if (withValueType) {
            if (src.categories.isNotEmpty()) {
                val categories = JsonObject()

                for ((category, modes) in src.categories) {
                    categories.add(category, context.serialize(modes.map { it.tag }))
                }

                obj.add("categories", categories)
            }

            obj.add("valueType", context.serialize(src.valueType))
            obj.addInteropMetadata(src)
        }

        return obj
    }

    companion object {
        @JvmField
        val INTEROP_SERIALIZER = ModeValueGroupSerializer(
            withValueType = true, includePrivate = true, includeNotAnOption = false
        )

        @JvmField
        val FILE_SERIALIZER = ModeValueGroupSerializer(
            withValueType = false, includePrivate = true, includeNotAnOption = true
        )

        @JvmField
        val PUBLIC_CONFIG_SERIALIZER = ModeValueGroupSerializer(
            withValueType = false, includePrivate = false, includeNotAnOption = true
        )
    }

}
