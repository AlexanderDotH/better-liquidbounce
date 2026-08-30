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
@file:JvmName("MinecraftInteractableTargetPortKt")

package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractablePlayerEligibility
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableResolvedTarget
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetBlockFilter
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetRejection
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetRequest
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetResolution
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetResolver
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.InteractableTargetValidation
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.MinecraftInteractableTargetWorldAdapter
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.target.toTargetBlockKey
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeCam
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.collection.Filter

internal class TargetPortAdapter : ControllerTargetPort<InteractableRuntimeTarget> {
    private val resolver = InteractableTargetResolver(MinecraftInteractableTargetWorldAdapter)

    override fun acquire(settings: InteractableSettingsSnapshot): ControllerTargetResult<InteractableRuntimeTarget> {
        val player = mc.player ?: return rejected(InteractableTargetRejection.WORLD_UNAVAILABLE)
        val request = InteractableTargetRequest(
            maxRange = settings.maxRange,
            normalInteractionAvailable = false,
            player = InteractablePlayerEligibility(
                alive = player.isAlive && !player.isDeadOrDying,
                spectator = player.isSpectator,
                passenger = player.isPassenger,
                detachedCamera = ModuleFreeCam.running,
                remoteMovementAvailable = true,
            ),
            containerVehicles = settings.containerVehicles,
            blockFilter = settings.filter.toTargetFilter(),
        )
        return when (val resolution = resolver.acquire(request)) {
            is InteractableTargetResolution.Acquired -> ControllerTargetResult.Acquired(resolution.target)
            is InteractableTargetResolution.Rejected -> rejected(resolution.reason)
        }
    }

    override fun validate(target: InteractableRuntimeTarget): Boolean =
        resolver.validate(target.resolved.lock) === InteractableTargetValidation.Valid

    override fun validateWhileHolding(target: InteractableRuntimeTarget): Boolean =
        resolver.validate(target.resolved.lock, allowInteractionStateChange = true) === InteractableTargetValidation.Valid

    private fun rejected(reason: InteractableTargetRejection) = ControllerTargetResult.Rejected(reason.name)
}

private val InteractableRuntimeTarget.resolved: InteractableResolvedTarget
    get() = this as InteractableResolvedTarget

private fun InteractableBlockFilter.toTargetFilter(): InteractableTargetBlockFilter {
    val keys = blocks.mapTo(mutableSetOf()) { it.toTargetBlockKey() }
    return when (mode) {
        Filter.BLACKLIST -> InteractableTargetBlockFilter.blacklist(keys)
        Filter.WHITELIST -> InteractableTargetBlockFilter.whitelist(keys)
    }
}
