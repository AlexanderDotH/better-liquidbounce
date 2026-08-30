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

package net.ccbluex.liquidbounce.features.module.modules.render.animations

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.events.PlayerStrideEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.world.InteractionHand

fun shouldApplyOffHandTransform(
    hand: InteractionHand,
    isInBothHands: Boolean,
    offHandRunning: Boolean,
): Boolean {
    return offHandRunning && (hand == InteractionHand.OFF_HAND || isInBothHands)
}

/**
 * Animations module
 *
 * This module affects item animations. It allows the user to customize the animation.
 * If you are looking forward to contribute to this module, please name your animation with a reasonable name.
 * Do not name them after clients or yourself.
 * Please credit from where you got the animation from and make sure they are willing to contribute.
 * If they are not willing to contribute, please do not add the animation to this module.
 */
@Suppress("MagicNumber")
object ModuleAnimations : ClientModule("Animations", ModuleCategories.RENDER, aliases = listOf("ViewModel")) {

    init {
        tree(MainHand)
        tree(OffHand)
        tree(EquipOffset)
        tree(SwingAnimations)
    }

    object MainHand : ToggleableValueGroup(this, "MainHand", false) {
        val mainHandItemScale by float("ItemScale", 0f, -5f..5f)
        val mainHandX by float("X", 0f, -5f..5f)
        val mainHandY by float("Y", 0f, -5f..5f)
        val mainHandPositiveX by float("PositiveRotationX", 0f, -50f..50f)
        val mainHandPositiveY by float("PositiveRotationY", 0f, -50f..50f)
        val mainHandPositiveZ by float("PositiveRotationZ", 0f, -50f..50f)
    }

    object OffHand : ToggleableValueGroup(this, "OffHand", false) {
        val offHandItemScale by float("ItemScale", 0f, -5f..5f)
        val offHandX by float("X", 0f, -1f..1f)
        val offHandY by float("Y", 0f, -1f..1f)
        val OffHandPositiveX by float("PositiveRotationX", 0f, -50f..50f)
        val OffHandPositiveY by float("PositiveRotationY", 0f, -50f..50f)
        val OffHandPositiveZ by float("PositiveRotationZ", 0f, -50f..50f)
    }

    val swingDuration by int("SwingDuration", 6, 1..20)

    /**
     * A choice that allows the user to choose the animation that will be used during the blocking
     * of a sword.
     * This choice is only used when the [ModuleSwordBlock] module is enabled.
     */

    val swingAnimation = SwingAnimations
    val blockAnimationChoice = choices(
        "BlockingAnimation", OneSevenAnimation, arrayOf(
            OneSevenAnimation,
            PushdownAnimation,
            SigmaAnimation,
            ExhibitionAnimation,
            AvatarAnimation,
            DortwareAnimation
        )
    )

    object EquipOffset : ToggleableValueGroup(this, "EquipOffset", true) {
        private val ignore by multiEnumChoice("Ignore",
            Ignores.BLOCKING,
            Ignores.PLACE
        )

        val ignoreBlocking get() = Ignores.BLOCKING in ignore
        val ignorePlace get() = Ignores.PLACE in ignore
        val ignoreAmount get() = Ignores.AMOUNT in ignore

        private enum class Ignores(
            override val tag: String
        ) : Tagged {
            BLOCKING("Blocking"),
            PLACE("Place"),
            AMOUNT("Amount")
        }
    }

    /**
     * if true, the walk animation will also be applied in the air.
     */
    private val airWalker by boolean("AirWalker", false)

    @Suppress("unused")
    val strideHandler = handler<PlayerStrideEvent> { event ->
        if (airWalker) {
            event.strideForce = 0.1.coerceAtMost(player.deltaMovement.horizontalDistance()).toFloat()
        }
    }

    object OneSevenAnimation : OneSevenAnimationMode()
    object PushdownAnimation : PushdownAnimationMode()
    object SigmaAnimation : SigmaAnimationMode()
    object ExhibitionAnimation : ExhibitionAnimationMode()
    object AvatarAnimation : AvatarAnimationMode()
    object DortwareAnimation : DortwareAnimationMode()
}
