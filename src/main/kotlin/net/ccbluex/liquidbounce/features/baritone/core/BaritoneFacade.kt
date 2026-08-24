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

package net.ccbluex.liquidbounce.features.baritone.core

/**
 * Stable application port for Baritone.
 *
 * Infrastructure adapters are the only layer allowed to depend on Baritone or Minecraft classes. Callers must
 * execute mutating operations on the Minecraft thread. Implementations return immutable snapshots and collections.
 */
@Suppress("TooManyFunctions")
interface BaritoneFacade {

    fun capability(): BaritoneCapability

    fun snapshot(): BaritoneSnapshot

    fun route(): BaritoneRoute

    fun submitTask(task: BaritoneTaskRequest): BaritoneResult<BaritoneSnapshot>

    fun control(action: BaritoneControlAction): BaritoneResult<BaritoneSnapshot>

    fun settings(): List<BaritoneSetting>

    fun setting(name: BaritoneSettingName): BaritoneSetting?

    fun updateSetting(
        name: BaritoneSettingName,
        value: BaritoneSettingValue,
    ): BaritoneResult<BaritoneSetting>

    fun resetSetting(name: BaritoneSettingName): BaritoneResult<BaritoneSetting>

    fun resetSettings(): BaritoneResult<List<BaritoneSetting>>

    fun deleteSetting(name: BaritoneSettingName): BaritoneResult<Unit>

    fun waypoints(): List<BaritoneWaypoint>

    fun addWaypoint(waypoint: BaritoneWaypointDraft): BaritoneResult<BaritoneWaypoint>

    fun deleteWaypoint(selector: BaritoneWaypointSelector): BaritoneResult<Unit>

    fun executeCommand(command: String): BaritoneResult<BaritoneCommandOutput>

    fun completions(input: String, cursor: Int = input.length): BaritoneResult<List<String>>

    /**
     * Applies a lifecycle boundary. Cleanup events cancel all pathing and clear Baritone input keys. A dimension
     * change only invalidates presentation route state so portal-spanning tasks can continue.
     */
    fun lifecycle(event: BaritoneLifecycleEvent): BaritoneResult<Unit>

    /** Clears every Baritone-controlled input key without changing the active task. */
    fun clearAllKeys(): BaritoneResult<Unit>
}

enum class BaritoneControlAction {
    PAUSE,
    RESUME,
    CANCEL,
}

enum class BaritoneLifecycleEvent {
    DISABLE,
    PANIC,
    DEATH,
    DISCONNECT,
    DIMENSION_CHANGE,
    SHUTDOWN,
}

sealed interface BaritoneResult<out T> {

    data class Success<out T>(val value: T) : BaritoneResult<T>

    data class Failure(val error: BaritoneError) : BaritoneResult<Nothing>
}

enum class BaritoneErrorCategory {
    VALIDATION,
    CONFLICT,
    UNAVAILABLE,
    INTERNAL,
}

enum class BaritoneErrorCode(val category: BaritoneErrorCategory) {
    INVALID_REQUEST(BaritoneErrorCategory.VALIDATION),
    INVALID_FIELD(BaritoneErrorCategory.VALIDATION),
    NOT_FOUND(BaritoneErrorCategory.VALIDATION),
    UNSUPPORTED(BaritoneErrorCategory.VALIDATION),
    INVALID_STATE(BaritoneErrorCategory.CONFLICT),
    COMMAND_FAILED(BaritoneErrorCategory.CONFLICT),
    UNAVAILABLE(BaritoneErrorCategory.UNAVAILABLE),
    INTERNAL_ERROR(BaritoneErrorCategory.INTERNAL),
}

data class BaritoneError(
    val code: BaritoneErrorCode,
    val message: String,
    val field: String? = null,
) {
    val category: BaritoneErrorCategory
        get() = code.category

    init {
        require(message.isNotBlank()) { "Baritone error messages cannot be blank" }
        require(field == null || field.isNotBlank()) { "Baritone error fields cannot be blank" }
    }
}

class BaritoneCommandOutput(messages: Collection<String>) {

    val messages: List<String> = immutableListCopy(messages)

    override fun equals(other: Any?): Boolean = other is BaritoneCommandOutput && messages == other.messages

    override fun hashCode(): Int = messages.hashCode()

    override fun toString(): String = "BaritoneCommandOutput(messages=$messages)"
}
