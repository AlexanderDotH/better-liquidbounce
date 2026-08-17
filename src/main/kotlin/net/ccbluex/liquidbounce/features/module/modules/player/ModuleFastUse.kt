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
package net.ccbluex.liquidbounce.features.module.modules.player

import com.google.gson.JsonObject
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSpearKill
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.utils.client.Timer
import net.ccbluex.liquidbounce.utils.entity.moving
import net.ccbluex.liquidbounce.utils.entity.opposite
import net.ccbluex.liquidbounce.utils.item.isConsumable
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.CRITICAL_MODIFICATION
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items

/**
 * FastUse module
 *
 * Allows you to use items faster on legacy servers.
 */

object ModuleFastUse : ClientModule("FastUse", ModuleCategories.PLAYER, aliases = listOf("FastEat")) {

    private object Food : ToggleableValueGroup(this, "Food", true) {

        val modes = choices("Mode", Immediate, arrayOf(Immediate, ItemUseTime))

        private val conditions by multiEnumChoice("Conditions", UseConditions.NOT_IN_THE_AIR)
        private val stopInput by boolean("StopInput", false)

        /**
         * The move packet type to send.
         *
         * @see MovePacketType
         * @see ServerboundMovePlayerPacket
         *
         * [MovePacketType.FULL] is the most likely to bypass, since vanilla 1.17+ clients
         * typically send those when using items. Most anticheats have excluded 1.17+ clients
         * from timer checks.
         *
         * AntiCheat: Grim (Tested 2.5.34), Vulcan, Vanilla
         * Tested on: eu.loyisa.cn, anticheat-test.com
         * Usable Client version: 1.17-1.20.4
         * Usable Server version: >=1.8.9
         * A: Legacy servers depend on the clientside tickrate to calculate eating speed.
         * Grim exemption: https://github.com/GrimAnticheat/Grim/blob/9660021d024a54634605fbcdf7ce1d631b442da1/src/main/java/ac/grim/grimac/checks/impl/movement/TimerCheck.java#L99
         */
        private val packetType by enumChoice("PacketType", MovePacketType.FULL)

        val accelerateNow: Boolean
            get() {
                if (!running) return false

                return shouldAccelerateFastUseFood(
                    foodRunning = true,
                    hasBlockingCondition = conditions.any { it.meetsConditions() },
                    isUsingItem = player.isUsingItem,
                    isConsumable = player.useItem.isConsumable,
                )
            }

        val shouldStopInput: Boolean
            get() = shouldStopFastUseFoodInput(running, stopInput)

        private object Immediate : Mode("Immediate") {

            override val parent: ModeValueGroup<Mode>
                get() = modes

            val delay by int("Delay", 0, 0..10, "ticks")
            val timer by float("Timer", 1f, 0.1f..5f)

            /**
             * This is the amount of times the packet is sent per tick.
             *
             * Having [speed] as 20 means that the server will simulate eating process 20 times each tick.
             */
            val speed by int("Speed", 20, 1..35, "packets")

            @Suppress("unused")
            val repeatable = tickHandler {
                if (!Food.accelerateNow) return@tickHandler
                val consumedItem = player.useItem

                Timer.requestTimerSpeed(
                    timer, Priority.IMPORTANT_FOR_USAGE_1, ModuleFastUse,
                    resetAfterTicks = 1 + delay
                )

                waitTicks(delay)
                if (!Food.accelerateNow || player.useItem !== consumedItem) return@tickHandler

                repeat(speed) {
                    network.send(packetType.generatePacket())
                }
                player.releaseUsingItem()
            }
        }

        private object ItemUseTime : Mode("ItemUseTime") {

            override val parent: ModeValueGroup<Mode>
                get() = modes

            val consumeTime by int("ConsumeTime", 15, 0..20)
            val speed by int("Speed", 20, 1..35, "packets")

            @Suppress("unused")
            val repeatable = tickHandler {
                if (Food.accelerateNow && player.ticksUsingItem >= consumeTime) {
                    repeat(speed) {
                        network.send(packetType.generatePacket())
                    }

                    player.releaseUsingItem()
                }
            }
        }
    }

    private object Spear : ToggleableValueGroup(this, "Spear", true)

    private object Crossbow : ToggleableValueGroup(this, "Crossbow", false) {

        private val tickCooldown by int("TickCooldown", 1, 1..20)

        @Suppress("unused")
        val tickHandler = handler<GameTickEvent> {
            if (player.isUsingItem && player.activeItem.`is`(Items.CROSSBOW) && player.tickCount % tickCooldown == 0) {
                val hand = player.usedItemHand
                val containerId = player.inventoryMenu.containerId
                val slot = player.inventory.selectedSlot + Inventory.INVENTORY_SIZE

                interaction.handleContainerInput(
                    containerId,
                    slot,
                    Inventory.SLOT_OFFHAND,
                    ContainerInput.SWAP,
                    player
                )

                interaction.useItem(player, hand.opposite)

                interaction.handleContainerInput(
                    containerId,
                    slot,
                    Inventory.SLOT_OFFHAND,
                    ContainerInput.SWAP,
                    player
                )

                interaction.useItem(player, hand)
            }
        }
    }

    init {
        tagBy(Food.modes)
        treeAll(Food, Spear, Crossbow)
    }

    val accelerateNow: Boolean
        get() = Food.accelerateNow

    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent>(priority = CRITICAL_MODIFICATION) { event ->
        if (Food.shouldStopInput && mc.options.keyUse.isDown) {
            event.directionalInput = DirectionalInput.NONE
        }
    }

    @Suppress("unused")
    private val spearHandler = handler<GameTickEvent> {
        if (!Spear.running || !player.isUsingItem || !isFastUseSpear(player.useItem)) {
            return@handler
        }

        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return@handler
        if (shouldRefreshFastUseSpear(
                isUseKeyDown = mc.options.keyUse.isDown,
                ticksUsingItem = player.ticksUsingItem,
                damageUseDuration = kineticWeapon.computeDamageUseDuration(),
                spearKillControlsUse = ModuleSpearKill.controlsSpearUse,
            )
        ) {
            // Kinetic weapon conditions have a finite server-side duration. Restart the same hand
            // before it expires so the client and server agree on the fresh use window.
            val hand = player.usedItemHand
            interaction.releaseUsingItem(player)
            interaction.useItem(player, hand)
            return@handler
        }

        if (shouldReleaseFastUseSpear(
                spearKillRunning = ModuleSpearKill.running,
                ticksUsingItem = player.ticksUsingItem,
                delayTicks = kineticWeapon.delayTicks,
            )
        ) {
            interaction.releaseUsingItem(player)
        }
    }

    /**
     * Applies FastUse's visual spear readiness without changing the server-side use duration.
     *
     * This overload is used by the first-person renderer. Its hand argument avoids depending on the
     * renderer's cached item stack during swap transitions.
     */
    @JvmStatic
    fun getSpearAnimationTicks(hand: InteractionHand, originalTicks: Float): Float {
        // SpearKill owns the raised pose whenever it is actively driving the attack/visual.
        if (ModuleSpearKill.controlsSpearAnimation) {
            return ModuleSpearKill.getSpearAnimationTicks(hand, originalTicks)
        }

        if (!shouldRenderFastUseSpear(
                fastUseRunning = running,
                spearRunning = Spear.running,
                isUsingItem = player.isUsingItem,
                isUsingSpear = isFastUseSpear(player.useItem),
                usedHand = player.usedItemHand,
                renderedHand = hand,
                spearKillControlsAnimation = false,
            )
        ) {
            return originalTicks
        }

        val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return originalTicks
        return adjustedSpearAnimationTicks(originalTicks, kineticWeapon.delayTicks)
    }

    /**
     * Applies FastUse's visual spear readiness only to the local player's third-person render state.
     */
    @JvmStatic
    fun getSpearAnimationTicks(entity: LivingEntity, originalTicks: Float): Float =
        if (entity === player) getSpearAnimationTicks(player.usedItemHand, originalTicks) else originalTicks

    override fun prepareDeserialize(jsonObject: JsonObject) {
        super.prepareDeserialize(jsonObject)
        migrateLegacyFastUseConfig(jsonObject)
    }

    @Suppress("unused")
    private enum class UseConditions(
        override val tag: String,
        val meetsConditions: () -> Boolean
    ) : Tagged {
        NOT_IN_THE_AIR("NotInTheAir", {
            !player.onGround()
        }),
        NOT_DURING_MOVE("NotDuringMove", {
            player.moving
        }),
        NOT_DURING_REGENERATION("NotDuringRegeneration", {
            player.hasEffect(MobEffects.REGENERATION)
        })
    }
}
