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
package net.ccbluex.liquidbounce.features.baritone.adapter

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneError
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneErrorCode
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneLogLevel
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePhase
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneResult
import net.ccbluex.liquidbounce.features.baritone.core.BaritoneSettingName

internal inline fun <T> BaritoneAdapterContext.executeAdapterOperation(
    field: String? = null,
    fallbackCode: BaritoneErrorCode = BaritoneErrorCode.INTERNAL_ERROR,
    operation: () -> T,
): BaritoneResult<T> = try {
    BaritoneResult.Success(operation())
} catch (error: BaritoneAdapterException) {
    adapterFailure(error.code, error.message, error.field ?: field)
} catch (error: IllegalArgumentException) {
    adapterFailure(BaritoneErrorCode.INVALID_FIELD, error.message.orEmpty().ifBlank { "Invalid value" }, field)
} catch (error: IllegalStateException) {
    adapterFailure(BaritoneErrorCode.INVALID_STATE, error.message.orEmpty().ifBlank { "Invalid state" }, field)
} catch (error: Throwable) {
    appendAdapterLog(BaritoneLogLevel.ERROR, "Baritone operation failed: ${error.message.orEmpty()}")
    adapterFailure(fallbackCode, error.message.orEmpty().ifBlank { "Baritone operation failed" }, field)
}

internal fun BaritoneAdapterContext.rememberAdapterTaskFailure(failure: BaritoneResult.Failure) {
    observedPhase = BaritonePhase.FAILED
    lastFailure = failure.error
}

internal fun BaritoneAdapterContext.adapterSettingNotFound(
    name: BaritoneSettingName,
    cause: Throwable? = null,
) = BaritoneAdapterException(
    BaritoneErrorCode.NOT_FOUND,
    cause?.message ?: "Unknown Baritone setting: ${name.value}",
    name.value,
    cause,
)

internal fun <T> adapterFailure(
    code: BaritoneErrorCode,
    message: String,
    field: String? = null,
): BaritoneResult<T> = BaritoneResult.Failure(BaritoneError(code, message, field))

internal inline fun <T, R> BaritoneResult<T>.mapAdapterSuccess(transform: (T) -> R): BaritoneResult<R> = when (this) {
    is BaritoneResult.Success -> BaritoneResult.Success(transform(value))
    is BaritoneResult.Failure -> this
}

internal inline fun <T> BaritoneResult<T>.alsoAdapterFailure(
    action: (BaritoneResult.Failure) -> Unit,
): BaritoneResult<T> = also { if (it is BaritoneResult.Failure) action(it) }
