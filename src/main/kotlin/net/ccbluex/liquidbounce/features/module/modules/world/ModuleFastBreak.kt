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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.NoneMode
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.item.isMiningTool
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket

/**
 * FastBreak module
 *
 * Allows you to break blocks faster.
 */
object ModuleFastBreak : ClientModule("FastBreak", ModuleCategories.WORLD) {

    private const val VANILLA_DESTROY_DELAY = 5

    private val breakDamage by float("BreakDamage", 0.8f, 0.1f..1f)
    private val onlyTool by boolean("OnlyTool", false)

    private val modeChoice = choices("Mode", 0) { arrayOf(NoneMode(it), AbortAnother, Intave) }.apply(::tagBy)

    val repeatable = handler<GameTickEvent> {
        if (onlyTool && !player.mainHandItem.isMiningTool) {
            return@handler
        }

        interaction.destroyDelay = 0

        if (interaction.destroyProgress > breakDamage) {
            interaction.destroyProgress = 1f
        }
    }

    override fun onDisabled() {
        interaction.destroyDelay = VANILLA_DESTROY_DELAY
        super.onDisabled()
    }

    /**
     * Bypass Grim 2.3.48 anti-cheat
     * Tested on eu.loyisa.cn
     *
     * https://github.com/GrimAnticheat/Grim/issues/1296
     */
    object AbortAnother : Mode("AbortAnother") {

        override val parent: ModeValueGroup<Mode>
            get() = modeChoice

        val packetHandler = handler<PacketEvent> {
            if (onlyTool && !player.mainHandItem.isMiningTool) {
                return@handler
            }

            val packet = it.packet

            if (packet is ServerboundPlayerActionPacket &&
                packet.action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
            ) {
                val blockPos = packet.pos

                // Abort block break on the block above (which we are not breaking)
                network.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        blockPos.above(), packet.direction
                    )
                )
            }
        }

    }

    /**
     * Uses the Intave phase packet pattern for block breaking without moving the player into blocks.
     */
    object Intave : Mode("Intave") {

        override val parent: ModeValueGroup<Mode>
            get() = modeChoice

        private var miningTarget: MiningTarget? = null

        @Suppress("unused")
        private val packetHandler = handler<PacketEvent> {
            val packet = it.packet as? ServerboundPlayerActionPacket ?: return@handler

            when (packet.action) {
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK -> {
                    miningTarget = MiningTarget(packet.pos, packet.direction)
                }

                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK -> {
                    if (miningTarget?.pos == packet.pos) {
                        miningTarget = null
                    }
                }

                else -> Unit
            }
        }

        @Suppress("unused")
        private val tickHandler = handler<GameTickEvent> {
            val target = miningTarget ?: return@handler

            if (!canSpoofIntaveBreak() || world.getBlockState(target.pos).isAir) {
                miningTarget = null
                return@handler
            }

            network.send(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    target.pos,
                    target.direction
                )
            )
        }

        override fun disable() {
            miningTarget = null
        }

        private fun canSpoofIntaveBreak(): Boolean {
            return mc.options.keyAttack.isDown &&
                (!onlyTool || player.mainHandItem.isMiningTool)
        }

        private data class MiningTarget(val pos: BlockPos, val direction: Direction)
    }

}
