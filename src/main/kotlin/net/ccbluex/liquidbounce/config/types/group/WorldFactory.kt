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
package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.Vec3Value
import net.minecraft.core.Vec3i
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import org.joml.Vector2fc
import java.util.SequencedSet

abstract class WorldFactory protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : InputFactory(name, value, valueType, independentDescription, aliases) {

    fun block(name: String, default: Block) = value(name, default, ValueType.BLOCK)

    fun vec2f(name: String, default: Vector2fc) = value(name, default, ValueType.VECTOR2_F)

    @JvmOverloads
    fun vec3i(
        name: String,
        default: Vec3i = Vec3i.ZERO,
        useLocateButton: Boolean = true,
        aliases: List<String> = emptyList(),
    ): Value<Vec3i> = value(Vec3Value(name, aliases, default, useLocateButton, ValueType.VECTOR3_I))

    @JvmOverloads
    fun vec3d(
        name: String,
        default: Vec3 = Vec3.ZERO,
        useLocateButton: Boolean = true,
        aliases: List<String> = emptyList(),
    ): Value<Vec3> = value(Vec3Value(name, aliases, default, useLocateButton, ValueType.VECTOR3_D))

    fun <C : SequencedSet<Block>> blocks(name: String, default: C) =
        registryList(name, default, ValueType.BLOCK)

    fun item(name: String, default: Item) = value(name, default, ValueType.ITEM)

    fun <C : SequencedSet<Item>> items(name: String, default: C) =
        registryList(name, default, ValueType.ITEM)

    fun <C : MutableList<Item>> itemList(name: String, default: C) =
        registryMutableList(name, default, ValueType.ITEM)
}
