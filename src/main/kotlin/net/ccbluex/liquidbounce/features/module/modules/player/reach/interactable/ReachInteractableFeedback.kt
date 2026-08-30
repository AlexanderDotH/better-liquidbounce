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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableRuntimeStatus
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import net.ccbluex.liquidbounce.features.chat.notification
import net.minecraft.network.chat.Component
import java.util.Locale

internal fun ReachInteractableFeature.reportRuntimeStatus() {
    val current = runtime.status
    if (current == null || current == reportedStatus) return
    reportedStatus = current
    val message = current.feedbackMessage() ?: return
    notification("Reach Interactable", message, NotificationEvent.Severity.ERROR)
}

private fun InteractableRuntimeStatus.feedbackMessage(): Component? = when (this) {
    is InteractableRuntimeStatus.Failure -> failureMessage(reason)
    is InteractableRuntimeStatus.RecoveryStalled ->
        causeMessage("interactable.recoveryStalled", cause.name)
    is InteractableRuntimeStatus.Recovery ->
        causeMessage("interactable.recovering", cause.name)
    is InteractableRuntimeStatus.Terminated ->
        causeMessage("interactable.terminated", cause.name)
    is InteractableRuntimeStatus.Resynchronized -> ModuleReach.message(
        "interactable.resynchronized",
        position.x.formatCoordinate(),
        position.y.formatCoordinate(),
        position.z.formatCoordinate(),
    )
    is InteractableRuntimeStatus.State -> null
}

private fun causeMessage(key: String, cause: String) =
    ModuleReach.message(key, ModuleReach.message("interactable.cause.${cause.toFailureKey()}"))

private fun failureMessage(reason: String) = reason.openAttemptNumber()?.let { attempt ->
    ModuleReach.message("interactable.failure.openAttempt", attempt)
} ?: ModuleReach.message("interactable.failure.${reason.toFailureKey()}")

private fun String.openAttemptNumber(): Int? = takeIf {
    startsWith("OPEN_ATTEMPT_") && endsWith("_FAILED")
}?.removePrefix("OPEN_ATTEMPT_")?.removeSuffix("_FAILED")?.toIntOrNull()

private fun String.toFailureKey(): String = lowercase(Locale.ROOT)
    .split('_')
    .let { words ->
        words.first() + words.drop(1).joinToString("") { word ->
            word.replaceFirstChar { character -> character.uppercaseChar() }
        }
    }

private fun Double.formatCoordinate(): String = String.format(Locale.ROOT, "%.2f", this)
