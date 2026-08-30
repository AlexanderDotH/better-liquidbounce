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

import com.google.gson.JsonObject
import net.ccbluex.fastutil.forEachIsInstance
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.utils.text.toLowerCamelCase

abstract class Hierarchy protected constructor(
    name: String,
    value: MutableCollection<Value<*>>,
    valueType: ValueType,
    independentDescription: Boolean,
    aliases: List<String>,
) : Value<MutableCollection<Value<*>>>(
    name,
    aliases,
    defaultValue = value,
    valueType,
    independentDescription = independentDescription,
) {

    protected abstract val group: ValueGroup

    open fun prepareDeserialize(jsonObject: JsonObject) = Unit

    var base: ValueGroup? = null

    open val baseKey: String
        get() = "${ConfigSystem.KEY_PREFIX}.${name.toLowerCamelCase()}"

    open fun walkInit() {
        inner.forEachIsInstance<ValueGroup> { valueGroup ->
            valueGroup.walkInit()
        }
    }

    fun walkKeyPath(previousBaseKey: String? = null) {
        key = if (previousBaseKey != null) {
            "$previousBaseKey.${name.toLowerCamelCase()}"
        } else {
            constructBaseKey()
        }

        for (currentValue in inner) {
            if (currentValue is ValueGroup) {
                currentValue.walkKeyPath(key)
            } else {
                currentValue.key = "$key.${currentValue.name.toLowerCamelCase()}"
            }

            if (currentValue is ModeValueGroup<*>) {
                val currentKey = currentValue.key
                currentValue.modes.forEach { choice -> choice.walkKeyPath(currentKey) }
            }
        }
    }

    private fun constructBaseKey(): String {
        val values = mutableListOf<String>()
        var current: ValueGroup? = group
        while (current != null) {
            val currentBase = current.base
            if (currentBase == null) {
                values.add(current.baseKey)
            } else {
                values.add(current.name.toLowerCamelCase())
            }
            current = currentBase
        }
        values.reverse()
        return values.joinToString(".")
    }

    @get:JvmName("getContainedValues")
    val containedValues: Array<Value<*>>
        get() = inner.toTypedArray()

    fun collectValuesRecursively(prefix: String = ""): Sequence<Value<*>> = sequence {
        val shouldFilterByPrefix = prefix.isNotBlank()

        suspend fun SequenceScope<Value<*>>.walk(current: ValueGroup) {
            if (shouldFilterByPrefix && !shouldWalkKey(current.key, prefix)) {
                return
            }
            for (value in current.inner) {
                when (value) {
                    is ToggleableValueGroup -> {
                        yield(value)
                        walk(value)
                    }
                    is ModeValueGroup<*> -> {
                        yield(value)
                        value.modes.forEach { walk(it) }
                    }
                    is ValueGroup -> walk(value)
                    else -> yield(value)
                }
            }
        }

        walk(group)
    }

    fun collectValueGroupsRecursively(prefix: String = ""): Sequence<ValueGroup> = sequence {
        val shouldFilterByPrefix = prefix.isNotBlank()

        suspend fun SequenceScope<ValueGroup>.walk(current: ValueGroup) {
            if (shouldFilterByPrefix && !shouldWalkKey(current.key, prefix)) {
                return
            }
            yield(current)
            for (value in current.inner) {
                when (value) {
                    is ModeValueGroup<*> -> {
                        walk(value)
                        value.modes.forEach { walk(it) }
                    }
                    is ValueGroup -> walk(value)
                }
            }
        }

        walk(group)
    }

    private fun shouldWalkKey(currentKey: String?, prefix: String): Boolean {
        if (currentKey == null) {
            return false
        }
        return currentKey.startsWith(prefix, ignoreCase = true) ||
            prefix.startsWith(currentKey, ignoreCase = true)
    }

    override fun restore() {
        inner.forEach(Value<*>::restore)
    }
}
