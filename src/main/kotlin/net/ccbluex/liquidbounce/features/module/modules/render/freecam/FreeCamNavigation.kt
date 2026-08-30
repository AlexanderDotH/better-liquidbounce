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

package net.ccbluex.liquidbounce.features.module.modules.render.freecam

import com.mojang.blaze3d.platform.InputConstants
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.utils.input.isPressed
import net.ccbluex.liquidbounce.features.navigation.NavigationBaseValueGroup
import net.minecraft.world.phys.Vec3

internal open class FreeCamNavigation(
    parent: EventListener,
    private val cameraTarget: () -> Vec3?,
) : NavigationBaseValueGroup<Unit>(parent, "Navigation", false) {

    private val controlKey by key("Key", InputConstants.KEY_LCONTROL)

    val shouldBeGoing: Boolean
        get() = running && controlKey != InputConstants.UNKNOWN && controlKey.isPressed

    override fun createNavigationContext() = Unit

    override fun calculateGoalPosition(context: Unit): Vec3? = cameraTarget().takeIf { shouldBeGoing }
}
