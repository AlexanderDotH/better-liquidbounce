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
package net.ccbluex.liquidbounce.config.gson.serializer

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.Value

/** Adds localized UI metadata that must never leak into persisted or shared configs. */
internal fun JsonObject.addInteropMetadata(value: Value<*>) {
    value.key?.let { addProperty("key", it) }
    val descriptions = ValueInteropDescriptionResolver.resolve(value)
    addProperty("description", descriptions.description)
    addProperty("extendedDescription", descriptions.extendedDescription)
}
