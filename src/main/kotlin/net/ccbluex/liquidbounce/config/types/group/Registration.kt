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

import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.config.types.list.ItemListValue
import net.ccbluex.liquidbounce.config.types.list.ListValue
import net.ccbluex.liquidbounce.config.types.list.MutableListValue
import net.ccbluex.liquidbounce.config.types.list.RegistryListValue
import net.ccbluex.liquidbounce.config.types.list.RegistryMutableListValue
import net.ccbluex.liquidbounce.utils.client.logger
import java.util.SequencedSet

abstract class Registration protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : Hierarchy(name, value, valueType, independentDescription, aliases) {

    fun <T : ValueGroup> tree(valueGroup: T): T {
        require(valueGroup !is Config) {
            "ValueGroup '${valueGroup.name}' is a Config and cannot be added to another ValueGroup."
        }

        if (valueGroup.base != null) {
            logger.warn("ValueGroup '${valueGroup.name}' is already added to a parent '${valueGroup.base?.name}'")
        }

        value(valueGroup)
        valueGroup.base = group
        return valueGroup
    }

    fun <T : ValueGroup> treeAll(vararg valueGroups: T) {
        valueGroups.forEach(this::tree)
    }

    fun <T : ValueGroup> drop(valueGroup: T): T {
        require(valueGroup.base === group) {
            "ValueGroup '${valueGroup.name}' is not a child of '${group.name}'."
        }

        inner.remove(valueGroup)
        valueGroup.base = null
        return valueGroup
    }

    fun <T : Any> value(
        name: String,
        defaultValue: T,
        valueType: ValueType = ValueType.INVALID,
        aliases: List<String> = emptyList(),
    ) = value(Value(name, aliases = aliases, defaultValue = defaultValue, valueType = valueType))

    internal inline fun <T : MutableCollection<E>, reified E> list(
        name: String,
        defaultValue: T,
        valueType: ValueType,
    ) = value(ListValue(name, defaultValue, innerValueType = valueType, innerType = E::class.java))

    internal inline fun <T : MutableCollection<E>, reified E> mutableList(
        name: String,
        defaultValue: T,
        valueType: ValueType,
    ) = value(MutableListValue(name, defaultValue, valueType, E::class.java))

    internal inline fun <T : MutableSet<E>, reified E> itemList(
        name: String,
        defaultValue: T,
        items: Set<ItemListValue.NamedItem<E>>,
        valueType: ValueType,
    ) = value(ItemListValue(name, defaultValue, items, valueType, E::class.java))

    internal inline fun <T : SequencedSet<E>, reified E> registryList(
        name: String,
        defaultValue: T,
        valueType: ValueType,
    ) = value(RegistryListValue(name, defaultValue, valueType, E::class.java))

    internal inline fun <T : MutableList<E>, reified E> registryMutableList(
        name: String,
        defaultValue: T,
        valueType: ValueType,
    ) = value(RegistryMutableListValue(name, defaultValue, valueType, E::class.java))

    fun <V : Value<*>> value(value: V) = value.apply {
        group.inner.add(this)
        group.inclusionGroup?.let { this.inclusionGroup(it) }
    }
}
