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
package net.ccbluex.liquidbounce.event

import net.ccbluex.liquidbounce.annotations.Tag
import net.ccbluex.liquidbounce.config.gson.stategies.ProtocolExclude
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * A callable event
 */
abstract class Event {
    @ProtocolExclude
    var isCompleted: Boolean = false
        internal set
}

/**
 * A cancellable event
 */
abstract class CancellableEvent : Event() {
    /**
     * Let you know if the event is canceled
     *
     * @return state of cancel
     */
    var isCancelled: Boolean = false
        private set

    /**
     * Allows you to cancel an event
     */
    fun cancelEvent() {
        require(!isCompleted) { "Cannot cancel an event that has already been completed." }

        isCancelled = true
    }

}

/**
 * MixinEntityRenderState of event. Might be PRE or POST.
 */
enum class EventState(val stateName: String) {
    PRE("PRE"), POST("POST")
}

/**
 * Retrieves the name that the event is supposed to be associated with in JavaScript.
 */
val Class<out Event>.eventName: String
    get() = checkNotNull(EVENT_CLASS_TO_NAME[this]) {
        "The event '$name' is not registered."
    }

private val EVENT_CLASS_TO_NAME = ALL_EVENT_CLASSES.associateWithTo(
    ConcurrentHashMap<Class<out Event>, String>(ALL_EVENT_CLASSES.size)
) {
    it.declaredEventName()
}

@JvmField
internal val EVENT_NAME_TO_CLASS = ConcurrentSkipListMap<String, Class<out Event>>(
    String.CASE_INSENSITIVE_ORDER
).apply {
    ALL_EVENT_CLASSES.forEach { eventClass ->
        this[eventClass.declaredEventName()] = eventClass
    }
}

internal fun registerEventMetadata(eventClass: Class<out Event>) {
    val name = eventClass.declaredEventName()
    val existingClass = EVENT_NAME_TO_CLASS.putIfAbsent(name, eventClass)
    check(existingClass == null || existingClass == eventClass) {
        "The event name '$name' is already registered for '${existingClass?.name}'."
    }
    EVENT_CLASS_TO_NAME.putIfAbsent(eventClass, name)
}

private fun Class<out Event>.declaredEventName(): String =
    checkNotNull(getAnnotation(Tag::class.java)) {
        "The event '$name' does not declare @Tag."
    }.name
