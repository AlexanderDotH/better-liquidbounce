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
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.DelayPlayerModel
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.DelayedTransform
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeBhop
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeCriticals
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeJesus
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeKillAura
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeScaffold
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeSneak
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeSpinbot
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.FakeVelocity
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelActionState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelDelayState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelFakeBhopState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelFakeCriticalsState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelFakeJesusState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelFakeScaffoldState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelFakeSpinbotState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelFakeVelocityState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelHysteriaState
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.PlayerModelVisualTransform
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.interpolateBodyYaw
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateHeadYaw
import net.ccbluex.liquidbounce.utils.entity.interpolatePitch
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
@Suppress("TooManyFunctions")
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

    private var cachedTargetName = ""
    private var cachedTargetWorld: Any? = null
    private var cachedTargetTick = Long.MIN_VALUE
    private var cachedTargetResolved = false
    private var cachedTarget: RemotePlayer? = null

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        clearTargetCache()
        PlayerModelDelayState.reset()
        PlayerModelHysteriaState.reset()
        PlayerModelFakeScaffoldState.reset()
        PlayerModelFakeCriticalsState.reset()
        PlayerModelFakeJesusState.reset()
        PlayerModelFakeSpinbotState.reset()
        PlayerModelFakeBhopState.reset()
        FakeScaffold.clearRenderState()
        PlayerModelFakeVelocityState.reset()
    }

    override fun onDisabled() {
        super.onDisabled()
        clearTargetCache()
        PlayerModelDelayState.reset()
        PlayerModelHysteriaState.reset()
        PlayerModelFakeScaffoldState.reset()
        PlayerModelFakeCriticalsState.reset()
        PlayerModelFakeJesusState.reset()
        PlayerModelFakeSpinbotState.reset()
        PlayerModelFakeBhopState.reset()
        FakeScaffold.clearRenderState()
        PlayerModelFakeVelocityState.reset()
    }

    @JvmStatic
    fun setTargetName(name: String) {
        targetPlayer = name.trim()
        clearTargetCache()
    }

    @JvmStatic
    fun findTarget(): RemotePlayer? {
        if (!running) {
            clearTargetCache()
            return null
        }

        val name = targetPlayer.trim()
        if (name.isEmpty()) {
            clearTargetCache()
            return null
        }

        val level = player.level() ?: return null
        val tick = level.gameTime
        if (isTargetCacheFor(name, level, tick)) {
            return cachedTarget.takeIf { it?.isValidCachedTarget(name, level) == true }
        }

        cachedTargetName = name
        cachedTargetWorld = level
        cachedTargetTick = tick
        cachedTargetResolved = true
        cachedTarget = level.players().firstOrNull { it.isConfiguredTarget(name) } as? RemotePlayer
        return cachedTarget
    }

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

        val criticals = FakeCriticals.getActionState(entity)
        val jesus = FakeJesus.getActionState(entity)
        val scaffold = FakeScaffold.getActionState(entity)
        val bhop = FakeBhop.getActionState(entity)
        val crouching = FakeSneak.running || criticals?.crouching == true || scaffold?.crouching == true
        val groundPose = bhop?.groundPose == true ||
            jesus?.groundPose == true ||
            criticals?.groundPose == true ||
            scaffold?.groundPose == true
        val swingProgress = criticals?.swingProgress ?: scaffold?.swingProgress
        val armPose = criticals?.armPose ?: scaffold?.armPose

        if (!crouching && !groundPose && swingProgress == null && armPose == null) {
            return null
        }

        return PlayerModelActionState(
            crouching = crouching,
            groundPose = groundPose,
            swingProgress = swingProgress,
            armPose = armPose,
        )
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
        if (!running || entity !is RemotePlayer || entity === player) {
            return false
        }

        return entity.gameProfile.name.equals(targetPlayer.trim(), ignoreCase = true)
            && Appearance.hasSpoofedAppearance()
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

        val delayed = delayedTransform(entity)
        val aura = fakeKillAuraTransform(entity)
        val spinbot = fakeSpinbotTransform(entity)

        val basePosition = getBaseVisualPosition(entity, partialTicks)
        val velocityPositionActive = FakeVelocity.running && PlayerModelFakeVelocityState.hasPositionOverride(entity)
        val jesus = fakeJesusTransform(entity, partialTicks, basePosition)
        val bhopBasePosition = jesus?.position ?: basePosition
        val bhop = fakeBhopTransform(entity, partialTicks, bhopBasePosition, velocityPositionActive)
        val criticalBasePosition = jesus?.position ?: basePosition
        val criticals = fakeCriticalsTransform(entity, partialTicks, criticalBasePosition, velocityPositionActive)
        val scaffold = fakeScaffoldTransform(entity)

        val delayedTransform = delayed?.toVisualTransform()
        val rotationSource = selectRotationSource(entity, aura, spinbot, criticals, scaffold, bhop, delayedTransform)
        val visualPosition = criticals?.position ?: bhop?.position ?: jesus?.position ?: delayed?.pos
        val base = composeBaseTransform(entity, partialTicks, rotationSource, visualPosition)

        return applyFakeVelocity(entity, partialTicks, base)
    }

    @JvmStatic
    fun getAuxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3? {
        if (!isAmnesiaTarget(entity)) {
            return null
        }

        if (FakeVelocity.running) {
            PlayerModelFakeVelocityState.getVisualPosition(entity)?.let { return it }
        }

        return getBaseVisualPosition(entity, partialTicks)
    }

    private fun getBaseVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3 {
        if (DelayPlayerModel.running) {
            PlayerModelDelayState.getTransform(entity)?.pos?.let { return it }
        }

        return entity.interpolateCurrentPosition(partialTicks)
    }

    private fun delayedTransform(entity: LivingEntity): DelayedTransform? {
        if (!DelayPlayerModel.running) {
            return null
        }

        return PlayerModelDelayState.getTransform(entity)
    }

    private fun fakeKillAuraTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!FakeKillAura.running) {
            return null
        }

        return PlayerModelHysteriaState.getTransform(entity)
    }

    private fun fakeSpinbotTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!FakeSpinbot.running) {
            return null
        }

        return FakeSpinbot.getTransform(entity)
    }

    private fun fakeJesusTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
    ): PlayerModelVisualTransform? {
        if (!FakeJesus.running) {
            return null
        }

        return FakeJesus.getTransform(entity, partialTicks, basePosition)
    }

    private fun fakeBhopTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): PlayerModelVisualTransform? {
        if (!FakeBhop.running) {
            return null
        }

        return FakeBhop.getTransform(entity, partialTicks, basePosition, velocityPositionActive)
    }

    private fun fakeCriticalsTransform(
        entity: LivingEntity,
        partialTicks: Float,
        basePosition: Vec3,
        velocityPositionActive: Boolean,
    ): PlayerModelVisualTransform? {
        if (!FakeCriticals.running) {
            return null
        }

        return FakeCriticals.getTransform(
            entity = entity,
            partialTicks = partialTicks,
            basePosition = basePosition,
            velocityPositionActive = velocityPositionActive,
        )
    }

    private fun fakeScaffoldTransform(entity: LivingEntity): PlayerModelVisualTransform? {
        if (!FakeScaffold.running) {
            return null
        }

        return FakeScaffold.getTransform(entity)
    }

    private fun selectRotationSource(
        entity: LivingEntity,
        aura: PlayerModelVisualTransform?,
        spinbot: PlayerModelVisualTransform?,
        criticals: PlayerModelVisualTransform?,
        scaffold: PlayerModelVisualTransform?,
        bhop: PlayerModelVisualTransform?,
        delayed: PlayerModelVisualTransform?,
    ): PlayerModelVisualTransform? {
        val criticalsRotation = criticals?.takeIf { FakeCriticals.hasRotation(entity) }
        val bhopRotation = bhop?.takeIf { FakeBhop.hasRotation(entity) }
        return aura ?: spinbot ?: criticalsRotation ?: scaffold ?: bhopRotation ?: delayed ?: criticals
    }

    private fun composeBaseTransform(
        entity: LivingEntity,
        partialTicks: Float,
        rotationSource: PlayerModelVisualTransform?,
        visualPosition: Vec3?,
    ): PlayerModelVisualTransform? = when {
        rotationSource != null -> rotationSource.copy(position = visualPosition)
        visualPosition != null -> currentTransform(entity, partialTicks).copy(position = visualPosition)
        else -> null
    }

    private fun applyFakeVelocity(
        entity: LivingEntity,
        partialTicks: Float,
        base: PlayerModelVisualTransform?,
    ): PlayerModelVisualTransform? {
        if (!FakeVelocity.running) {
            return base
        }

        return PlayerModelFakeVelocityState.getTransform(entity, partialTicks, base) ?: base
    }

    private fun currentTransform(entity: LivingEntity, partialTicks: Float): PlayerModelVisualTransform =
        PlayerModelVisualTransform(
            position = null,
            bodyYaw = entity.interpolateBodyYaw(partialTicks),
            headYaw = entity.interpolateHeadYaw(partialTicks),
            pitch = entity.interpolatePitch(partialTicks),
        )

    private fun DelayedTransform.toVisualTransform(): PlayerModelVisualTransform =
        PlayerModelVisualTransform(
            position = pos,
            bodyYaw = bodyYaw,
            headYaw = headYaw,
            pitch = pitch,
        )

    private fun isTargetCacheFor(name: String, level: Any, tick: Long): Boolean =
        cachedTargetResolved &&
            cachedTargetWorld === level &&
            cachedTargetTick == tick &&
            cachedTargetName.equals(name, ignoreCase = true)

    private fun Player.isConfiguredTarget(name: String): Boolean =
        this is RemotePlayer &&
            this !== player &&
            !isRemoved &&
            gameProfile.name.equals(name, ignoreCase = true) &&
            !ModuleAntiBot.isBot(this)

    private fun RemotePlayer.isValidCachedTarget(name: String, level: Any): Boolean =
        !isRemoved &&
            level() === level &&
            gameProfile.name.equals(name, ignoreCase = true)

    private fun clearTargetCache() {
        cachedTarget = null
        cachedTargetName = ""
        cachedTargetWorld = null
        cachedTargetTick = Long.MIN_VALUE
        cachedTargetResolved = false
    }
}
