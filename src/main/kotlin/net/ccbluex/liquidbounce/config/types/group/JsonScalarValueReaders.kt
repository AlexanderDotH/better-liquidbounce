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

@file:JvmName("JsonValueFactoryKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.config.types.group

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.common.interop.PackedThemeColor
import net.ccbluex.liquidbounce.config.types.FileDialogMode
import java.io.File

internal fun ValueGroup.readJsonBoolean(name: String, json: JsonObject) {
    boolean(name, json["value"].asBoolean)
}

internal fun ValueGroup.readJsonInt(name: String, json: JsonObject) {
    val range = json["range"].asJsonObject
    int(name, json["value"].asInt, range["min"].asInt..range["max"].asInt, json.suffix())
}

internal fun ValueGroup.readJsonIntRange(name: String, json: JsonObject) {
    val value = json["value"].asJsonObject
    val range = json["range"].asJsonObject
    intRange(name, value["min"].asInt..value["max"].asInt, range["min"].asInt..range["max"].asInt, json.suffix())
}

internal fun ValueGroup.readJsonFloat(name: String, json: JsonObject) {
    val range = json["range"].asJsonObject
    float(name, json["value"].asFloat, range["min"].asFloat..range["max"].asFloat, json.suffix())
}

internal fun ValueGroup.readJsonFloatRange(name: String, json: JsonObject) {
    val value = json["value"].asJsonObject
    val range = json["range"].asJsonObject
    floatRange(
        name,
        value["min"].asFloat..value["max"].asFloat,
        range["min"].asFloat..range["max"].asFloat,
        json.suffix(),
    )
}

internal fun ValueGroup.readJsonText(name: String, json: JsonObject) {
    text(name, json["value"].asString)
}

internal fun ValueGroup.readJsonFile(name: String, json: JsonObject) {
    val value = json["value"].unlessNull()?.asString?.takeUnless(String::isBlank)?.let(::File)
    val dialogMode = json["dialogMode"].unlessNull()?.asString?.let(FileDialogMode::valueOf)
        ?: FileDialogMode.OPEN_FILE
    val extensions = json["supportedExtensions"].unlessNull()?.asJsonArray?.mapTo(linkedSetOf()) { it.asString }
    file(name, value, dialogMode, extensions)
}

internal fun ValueGroup.readJsonColor(name: String, json: JsonObject) {
    color(name, PackedThemeColor(json["value"].asInt))
}

private fun JsonObject.suffix(): String = get("suffix")?.asString ?: ""

private fun com.google.gson.JsonElement?.unlessNull() = this?.takeUnless { it is JsonNull }
