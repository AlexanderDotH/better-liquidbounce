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
package net.ccbluex.liquidbounce.utils.block

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import java.util.function.Consumer

enum class SwingMode(
    override val tag: String,
    val serverSwing: Boolean,
) : Tagged, Consumer<InteractionHand> {

    DO_NOT_HIDE("DoNotHide", true),
    HIDE_BOTH("HideForBoth", false),
    HIDE_CLIENT("HideForClient", true),
    HIDE_SERVER("HideForServer", false);

    fun swing(hand: InteractionHand) = accept(hand)

    override fun accept(hand: InteractionHand) {
        when (this) {
            DO_NOT_HIDE -> player.swing(hand)
            HIDE_BOTH -> {}
            HIDE_CLIENT -> network.send(ServerboundSwingPacket(hand))
            HIDE_SERVER -> player.swing(hand, false)
        }
    }
}

val BlockHitResult.targetBlockPos: BlockPos get() = blockPos.relative(direction)
