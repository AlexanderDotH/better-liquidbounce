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

package net.ccbluex.liquidbounce.features.module.modules.`fun`

import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.Appearance
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.AmnesiaFeatureRuntimeAdapter
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.AmnesiaPlayerModelAdapter
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.DelayPlayerModel
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeBhop
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeCriticals
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeJesus
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeKillAura
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeScaffold
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeSneak
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeSpinbot
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeVelocity
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract.AmnesiaRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime.AmnesiaActionStateResolver
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime.AmnesiaRuntimeReset
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime.AmnesiaTargetResolver
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime.AmnesiaVisualTransformResolver
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelActionState
import net.ccbluex.liquidbounce.render.playermodel.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.phys.Vec3

/**
 * Amnesia module
 *
 * Applies client-side visual effects to a selected player.
 */
object ModuleAmnesia : ClientModule("Amnesia", ModuleCategories.FUN) {

    private var targetPlayer by text("Target", "")

    private val appearance = tree(Appearance)
    private val delayPlayerModel = tree(DelayPlayerModel)
    private val fakeKillAura = tree(FakeKillAura)
    private val fakeSpinbot = tree(FakeSpinbot)
    private val fakeBhop = tree(FakeBhop)
    private val fakeCriticals = tree(FakeCriticals)
    private val fakeJesus = tree(FakeJesus)
    private val fakeScaffold = tree(FakeScaffold)
    private val fakeSneak = tree(FakeSneak)
    private val fakeVelocity = tree(FakeVelocity)

    private val targetResolver = AmnesiaTargetResolver()

    init {
        AmnesiaPlayerModelAdapter.install()
        AmnesiaRuntimeBridge.install(AmnesiaFeatureRuntimeAdapter)
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        resetRuntime()
    }

    override fun onDisabled() {
        super.onDisabled()
        resetRuntime()
    }

    @JvmStatic
    fun setTargetName(name: String) {
        targetPlayer = name.trim()
        targetResolver.clear()
    }

    @JvmStatic
    fun findTarget(): RemotePlayer? = targetResolver.findTarget(running, targetPlayer, player)

    @JvmStatic
    fun isAmnesiaTarget(entity: LivingEntity): Boolean {
        if (!running) {
            return false
        }

        if (entity !is RemotePlayer || entity === player) {
            return false
        }

        val target = findTarget() ?: return false
        return entity.id == target.id
    }

    @JvmStatic
    fun shouldFakeSneak(entity: LivingEntity): Boolean =
        FakeSneak.running && isAmnesiaTarget(entity)

    @JvmStatic
    fun getActionState(entity: LivingEntity): PlayerModelActionState? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        return AmnesiaActionStateResolver.resolve(entity)
    }

    @JvmStatic
    fun getSpoofedName(entity: Player): String? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        return Appearance.spoofName
    }

    @JvmStatic
    fun getSpoofedDisplayName(entity: Player, original: Component): Component? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        return Appearance.displayName(original)
    }

    @JvmStatic
    fun hasSpoofedAppearance(entity: Player): Boolean {
        return targetResolver.matchesConfiguredTarget(running, targetPlayer, entity, player) &&
            Appearance.hasSpoofedAppearance()
    }

    @JvmStatic
    fun getSpoofedSkin(entity: AbstractClientPlayer): PlayerSkin? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        return Appearance.skin()
    }

    @JvmStatic
    fun getVisualTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        val partialTicks = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        return AmnesiaVisualTransformResolver.resolve(entity, partialTicks)
    }

    @JvmStatic
    fun getAuxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        return AmnesiaVisualTransformResolver.auxiliaryPosition(entity, partialTicks)
    }

    private fun resetRuntime() {
        targetResolver.clear()
        AmnesiaRuntimeReset.reset()
    }
}
