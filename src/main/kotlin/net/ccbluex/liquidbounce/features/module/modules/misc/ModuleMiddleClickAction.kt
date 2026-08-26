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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MouseButtonEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipDirection
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipMiddleClickInput
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.ccbluex.liquidbounce.utils.raytracing.isLookingAtEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.lwjgl.glfw.GLFW

/**
 * MiddleClickAction module
 *
 * Allows you to perform actions with middle clicks.
 */
object ModuleMiddleClickAction : ClientModule(
    "MiddleClickAction",
    ModuleCategories.MISC,
    aliases = listOf("FriendClicker", "MiddleClickPearl")
) {

    init {
        doNotIncludeAlways()
    }

    private val mode = modes(
        this,
        "Mode",
        FriendClicker,
        arrayOf(FriendClicker, Pearl, AmnesiaTarget, NukerBlock, Smart),
    )

    @JvmStatic
    fun cancelPick(): Boolean = Pearl.cancelPick() || NukerBlock.cancelPick() || Smart.cancelPick()

    internal fun isSmartVClipLockActive(): Boolean {
        return running && mode.activeMode === Smart && Smart.VClipLock.enabled && ModuleVClip.running
    }

    internal fun isSmartVClipModifierHeld(): Boolean {
        return isSmartVClipLockActive() && Smart.VClipLock.isHeld
    }

    internal fun resolveSmartVClipDirection(
        jumpPressed: Boolean,
        shiftPressed: Boolean,
        repeatDelayTicks: Int,
    ): VClipDirection? {
        if (!isSmartVClipLockActive()) return null
        return Smart.VClipLock.resolveDirection(jumpPressed, shiftPressed, repeatDelayTicks)
    }

    internal fun resetSmartVClipLock() = Smart.VClipLock.reset()

    override fun onDisabled() {
        Pearl.disable()
        Smart.reset()
    }

    object Pearl : Mode("Pearl") {

        private val slotResetDelay by int("SlotResetDelay", 1, 0..10, "ticks")
        private val stopOnSubmit by floatRange("StopOnSubmit", 85F..90F, 60F..90F, "Pitch")
        private var wasPressed = false

        val repeatable = handler<GameTickEvent> {
            if (mc.gui.screen() != null) {
                reset()
                return@handler
            }

            if (player.xRot in stopOnSubmit) {
                reset()
                return@handler
            }

            val pickup = mc.options.keyPickItem.isDown

            if (pickup) {
                // visually select the slot
                val slot = Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) ?: return@handler
                SilentHotbar.selectSlotSilently(this, slot, slotResetDelay)
                wasPressed = true
            } else if (wasPressed) { // the key was released
                Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL)?.let {
                    useHotbarSlotOrOffhand(it, slotResetDelay)
                }
                reset()
            }
        }

        @Suppress("unused")
        private val handler = handler<WorldChangeEvent> {
            reset()
        }

        override fun disable() = reset()

        private fun reset() {
            wasPressed = false
            SilentHotbar.resetSlot(this)
        }

        fun cancelPick(): Boolean {
            return ModuleMiddleClickAction.running &&
                mode.activeMode == this &&
                Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) != null
        }

        override val parent: ModeValueGroup<*>
            get() = mode

    }

    object FriendClicker : Mode("FriendClicker") {

        private val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)

        private var clicked = false

        val repeatable = handler<GameTickEvent> {
            val entity = findPlayerInCrosshair(pickUpRange) ?: return@handler

            val pickup = mc.options.keyPickItem.isDown

            if (pickup && !clicked) {
                toggleFriend(entity)
            }

            clicked = pickup
        }

        override val parent: ModeValueGroup<*>
            get() = mode

    }

    object AmnesiaTarget : Mode("AmnesiaTarget", aliases = listOf("Amnesia Target")) {

        private val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)

        @Suppress("unused")
        private val middleClickHandler = handler<MouseButtonEvent> { event ->
            if (mc.gui.screen() != null) {
                return@handler
            }

            if (event.action != GLFW.GLFW_PRESS || event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                return@handler
            }

            val entity = findPlayerInCrosshair(pickUpRange) ?: return@handler
            setAmnesiaTarget(entity)
        }

        override val parent: ModeValueGroup<*>
            get() = mode

    }

    object NukerBlock : Mode("NukerBlock", aliases = listOf("Nuker Block")) {

        @Suppress("unused")
        private val middleClickHandler = handler<MouseButtonEvent> { event ->
            if (event.screen != null ||
                event.action != GLFW.GLFW_PRESS ||
                event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE ||
                !ModuleNuker.running
            ) {
                return@handler
            }

            val hitResult = mc.hitResult as? BlockHitResult ?: return@handler
            if (hitResult.type != HitResult.Type.BLOCK) {
                return@handler
            }

            selectNukerBlock(hitResult)
        }

        fun cancelPick(): Boolean {
            return ModuleMiddleClickAction.running && mode.activeMode == this && ModuleNuker.running
        }

        override val parent: ModeValueGroup<*>
            get() = mode

    }

    object Smart : Mode("Smart") {

        object FriendClicker : ToggleableValueGroup(this@Smart, "FriendClicker", true) {
            val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)
        }

        object Pearl : ToggleableValueGroup(this@Smart, "Pearl", true) {
            val slotResetDelay by int("SlotResetDelay", 1, 0..10, "ticks")
            val stopOnSubmit by floatRange("StopOnSubmit", 85F..90F, 60F..90F, "Pitch")
            private val controller = MiddleClickPearlController()

            fun press(): Boolean = controller.press {
                if (player.xRot in stopOnSubmit) return@press false

                val slot = Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL) ?: return@press false
                SilentHotbar.selectSlotSilently(this, slot, slotResetDelay)
            }

            fun release(): Boolean = controller.release {
                Slots.OffhandWithHotbar.findSlot(Items.ENDER_PEARL)?.let { slot ->
                    useHotbarSlotOrOffhand(slot, slotResetDelay)
                }
            }

            fun cancelPick(): Boolean = controller.cancelsVanillaPick

            fun reset() {
                controller.reset()
                SilentHotbar.resetSlot(this)
            }
        }

        object AmnesiaTarget : ToggleableValueGroup(this@Smart, "AmnesiaTarget", true) {
            val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)
        }

        object NukerBlock : ToggleableValueGroup(this@Smart, "NukerBlock", true)

        internal object VClipLock : ToggleableValueGroup(this@Smart, "VClipLock", true) {
            private val input = VClipMiddleClickInput()

            val isHeld: Boolean
                get() = input.isHeld

            fun press(): Boolean {
                if (!enabled || !ModuleVClip.running) return false
                input.press()
                return true
            }

            fun release() = input.release()

            fun resolveDirection(
                jumpPressed: Boolean,
                shiftPressed: Boolean,
                repeatDelayTicks: Int,
            ): VClipDirection? {
                return input.resolveDirection(jumpPressed, shiftPressed, repeatDelayTicks)
            }

            fun reset() = input.reset()

            override fun onDisabled() = reset()
        }

        private var cancelsVanillaPick = false

        init {
            tree(FriendClicker)
            tree(Pearl)
            tree(AmnesiaTarget)
            tree(NukerBlock)
            tree(VClipLock)
        }

        @Suppress("unused")
        private val middleClickHandler = handler<MouseButtonEvent> { event ->
            if (!event.isMiddleButton) return@handler

            if (event.screen != null) {
                reset()
                return@handler
            }

            when {
                event.isPressed -> cancelsVanillaPick = handlePress()
                event.isReleased -> handleRelease()
            }
        }

        @Suppress("unused")
        private val screenHandler = handler<GameTickEvent> {
            if (mc.gui.screen() != null) reset()
        }

        @Suppress("unused")
        private val worldHandler = handler<WorldChangeEvent> {
            reset()
        }

        fun cancelPick(): Boolean {
            if (!ModuleMiddleClickAction.running || mode.activeMode !== this) return false

            val shouldCancel = cancelsVanillaPick || Pearl.cancelPick()
            cancelsVanillaPick = false
            return shouldCancel
        }

        fun reset() {
            cancelsVanillaPick = false
            Pearl.reset()
            VClipLock.reset()
        }

        override fun disable() = reset()

        override val parent: ModeValueGroup<*>
            get() = mode

        private fun handlePress(): Boolean {
            val amnesiaPlayer = acquireAmnesiaPlayer()
            val friendPlayer = acquireFriendPlayer()
            val action = MiddleClickSmartResolver.resolve(
                MiddleClickSmartInput(
                    target = classifyTarget(amnesiaPlayer, friendPlayer),
                    options = configuredOptions(),
                    friendTargetAcquired = friendPlayer != null,
                    amnesiaRunning = ModuleAmnesia.running,
                    amnesiaTargetAcquired = amnesiaPlayer != null,
                    nukerRunning = ModuleNuker.running,
                    vClipRunning = ModuleVClip.running,
                )
            )

            return execute(action, amnesiaPlayer, friendPlayer)
        }

        private fun handleRelease() {
            VClipLock.release()
            Pearl.release()
        }

        private fun acquireAmnesiaPlayer(): Player? {
            if (!AmnesiaTarget.enabled || !ModuleAmnesia.running) return null
            return findPlayerInCrosshair(AmnesiaTarget.pickUpRange)
        }

        private fun acquireFriendPlayer(): Player? {
            if (!FriendClicker.enabled) return null
            return findPlayerInCrosshair(FriendClicker.pickUpRange)
        }

        private fun classifyTarget(amnesiaPlayer: Player?, friendPlayer: Player?): MiddleClickSmartTarget {
            if (amnesiaPlayer != null || friendPlayer != null) return MiddleClickSmartTarget.PLAYER

            return when (mc.hitResult?.type) {
                HitResult.Type.ENTITY -> MiddleClickSmartTarget.PLAYER
                HitResult.Type.BLOCK -> MiddleClickSmartTarget.BLOCK
                else -> MiddleClickSmartTarget.AIR
            }
        }

        private fun configuredOptions() = MiddleClickSmartOptions(
            friendClicker = FriendClicker.enabled,
            pearl = Pearl.enabled,
            amnesiaTarget = AmnesiaTarget.enabled,
            nukerBlock = NukerBlock.enabled,
            vClipLock = VClipLock.enabled,
        )

        private fun execute(
            action: MiddleClickSmartAction,
            amnesiaPlayer: Player?,
            friendPlayer: Player?,
        ): Boolean = when (action) {
            MiddleClickSmartAction.FRIEND_CLICKER -> friendPlayer?.let(::toggleFriend) != null
            MiddleClickSmartAction.PEARL -> Pearl.press()
            MiddleClickSmartAction.AMNESIA_TARGET -> amnesiaPlayer?.let(::setAmnesiaTarget) == true
            MiddleClickSmartAction.NUKER_BLOCK -> selectCurrentNukerBlock()
            MiddleClickSmartAction.VCLIP_HOLD -> VClipLock.press()
            MiddleClickSmartAction.NONE -> false
        }

        private fun selectCurrentNukerBlock(): Boolean {
            if (!ModuleNuker.running) return false
            val hitResult = mc.hitResult as? BlockHitResult ?: return false
            if (hitResult.type != HitResult.Type.BLOCK) return false
            return selectNukerBlock(hitResult)
        }
    }

    private fun findPlayerInCrosshair(pickUpRange: Float): Player? {
        val rotation = player.rotation
        val entity = (findEntityInCrosshair(pickUpRange.toDouble(), rotation) { it is Player }
            ?: return null).entity as Player

        return entity.takeIf {
            isLookingAtEntity(
                toEntity = it,
                rotation = rotation,
                range = pickUpRange.toDouble(),
                throughWallsRange = 0.0,
            ) != null
        }
    }

    private fun toggleFriend(entity: Player) {
        val name = entity.scoreboardName
        val friend = FriendManager.Friend(name, null)

        if (FriendManager.isFriend(name)) {
            FriendManager.friends.remove(friend)
            notification("FriendClicker", message("removedFriend", name), NotificationEvent.Severity.INFO)
            return
        }

        FriendManager.friends.add(friend)
        notification("FriendClicker", message("addedFriend", name), NotificationEvent.Severity.INFO)
    }

    private fun setAmnesiaTarget(entity: Player): Boolean {
        val name = entity.gameProfile.name ?: return false
        ModuleAmnesia.setTargetName(name)
        notification(
            "MiddleClickAction",
            message("amnesiaTargetSet", name),
            NotificationEvent.Severity.INFO,
        )
        return true
    }

    private fun selectNukerBlock(hitResult: BlockHitResult): Boolean {
        val block = ModuleNuker.selectBlock(hitResult.blockPos) ?: return false
        notification(
            "MiddleClickAction",
            message("nukerBlockSet", block.name.string),
            NotificationEvent.Severity.INFO,
        )
        return true
    }

}
