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

package net.ccbluex.liquidbounce.config.types

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.ccbluex.liquidbounce.config.OptionalInclusion
import net.ccbluex.liquidbounce.config.autoconfig.AutoConfigContext
import net.ccbluex.liquidbounce.config.gson.stategies.Exclude
import net.ccbluex.liquidbounce.config.gson.stategies.ProtocolExclude
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.ValueChangedEvent
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.toLowerCamelCase
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlin.reflect.KProperty

abstract class ValueState<T : Any>(
    @SerializedName("name") val name: String,
    @Exclude @ProtocolExclude val aliases: List<String>,
    @Exclude @ProtocolExclude private var defaultValue: T,
    @Exclude val valueType: ValueType,
    @Exclude @ProtocolExclude var independentDescription: Boolean,
) {

    @SerializedName("value")
    internal var inner: T = defaultValue

    internal val loweredName
        get() = name.lowercase()

    @Exclude
    @ProtocolExclude
    private val listeners: MutableList<ValueListener<T>> = ObjectArrayList()

    @Exclude
    @ProtocolExclude
    private val changedListeners: MutableList<ValueChangedListener<T>> = ObjectArrayList()

    @Exclude
    @ProtocolExclude
    private val stateFlow = MutableStateFlow(inner)

    @Exclude
    @ProtocolExclude
    var doNotInclude: BooleanSupplier = { false }
        private set

    @Exclude
    @ProtocolExclude
    var inclusionGroup: OptionalInclusion? = null
        private set

    @Exclude
    @ProtocolExclude
    var notAnOption = false
        private set

    @Exclude
    @ProtocolExclude
    var isImmutable = false
        private set

    @Exclude
    var key: String? = null
        set(value) {
            field = value
            descriptionKey = value?.let(::descriptionKeyFor)
        }

    @Exclude
    @ProtocolExclude
    var descriptionKey: String? = null

    @Exclude
    open var description = Supplier {
        descriptionKey?.let { key -> translation(key).string }
    }

    fun asStateFlow(): StateFlow<T> = stateFlow

    fun checkIfInclude(): Boolean {
        if (doNotInclude.asBoolean) {
            return false
        }

        val group = inclusionGroup ?: return true
        return group in AutoConfigContext.includeConfiguration.optionalInclusions
    }

    operator fun getValue(owner: Any?, property: KProperty<*>) = get()

    operator fun setValue(owner: Any?, property: KProperty<*>, value: T) {
        set(value)
    }

    fun get() = inner

    fun set(value: T) {
        if (value != inner) {
            set(value) { inner = it }
        }
    }

    fun set(value: T, apply: ValueChangedListener<T>) {
        var acceptedValue = value
        runCatching {
            listeners.forEach { acceptedValue = it.apply(value) }
            if (isImmutable) {
                return
            }
        }.onSuccess {
            apply.accept(acceptedValue)
            EventManager.callEvent(ValueChangedEvent(asValue()))
            changedListeners.forEach { listener -> listener.accept(acceptedValue) }
            stateFlow.value = acceptedValue
        }.onFailure { exception ->
            logger.error("Failed to set $name from $inner to $value", exception)
        }
    }

    open fun restore() {
        set(defaultValue)
    }

    fun type() = valueType

    fun immutable(): Value<T> {
        isImmutable = true
        return asValue()
    }

    fun onChange(listener: ValueListener<T>): Value<T> {
        listeners += listener
        return asValue()
    }

    fun onChanged(listener: ValueChangedListener<T>): Value<T> {
        changedListeners += listener
        return asValue()
    }

    fun doNotIncludeAlways(): Value<T> {
        doNotInclude = BooleanSupplier { true }
        return asValue()
    }

    fun doNotIncludeWhen(condition: BooleanSupplier): Value<T> {
        doNotInclude = condition
        return asValue()
    }

    open fun inclusionGroup(group: OptionalInclusion): Value<T> {
        inclusionGroup = group
        return asValue()
    }

    fun notAnOption(): Value<T> {
        notAnOption = true
        return asValue()
    }

    fun independentDescription(): Value<T> {
        independentDescription = true
        return asValue()
    }

    open fun deserializeFrom(gson: Gson, element: JsonElement) {
        ValueJsonDeserializer.deserialize(this, gson, element)?.let(::set)
            ?: error("Failed to deserialize value")
    }

    open fun setByString(string: String) {
        val deserializer = requireNotNull(valueType.deserializer) {
            "Cannot deserialize values of type $valueType yet."
        }
        ValueStringDeserializer.deserialize<T>(deserializer, string)?.let(::set)
            ?: error("Failed to deserialize value of type $valueType")
    }

    override fun toString(): String = "${javaClass.simpleName}(name=$name, type=$valueType)"

    private fun descriptionKeyFor(value: String): String = if (independentDescription) {
        "liquidbounce.common.${name.toLowerCamelCase()}.description"
    } else {
        "$value.description"
    }

    @Suppress("UNCHECKED_CAST")
    private fun asValue(): Value<T> = this as Value<T>
}
