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
package net.ccbluex.liquidbounce.features.command.commands.ingame.fakeplayer

import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.getDamageFromExplosion
import net.ccbluex.liquidbounce.utils.entity.getEffectiveDamage
import net.ccbluex.liquidbounce.utils.network.entityIdC2SInteractOrAttack
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes

internal fun FakePlayerSession.handlePacket(event: PacketEvent) {
    if (fakePlayers.isEmpty()) {
        return
    }
    val packet = event.packet
    if (packet is ClientboundExplodePacket) {
        fakePlayers.forEach { fakePlayer ->
            val damage = fakePlayer.getDamageFromExplosion(pos = packet.center, power = packet.radius)
            fakePlayer.applyEstimatedDamage(damage)
        }
    }
    val interactEntityId = packet.entityIdC2SInteractOrAttack ?: return
    if (fakePlayers.any { fakePlayer -> interactEntityId == fakePlayer.id }) {
        event.cancelEvent()
    }
}

internal fun FakePlayerSession.handleAttack(event: AttackEntityEvent) {
    if (fakePlayers.none { fakePlayer -> fakePlayer.id == event.entity.id }) {
        return
    }
    val fakePlayer = event.entity as FakePlayer
    fakePlayer.applyEstimatedDamage(calculateAttackDamage(fakePlayer))
}

internal fun FakePlayerSession.handleTick() {
    if (!recording) {
        return
    }
    if (mc.level == null || mc.player == null) {
        chat(markAsError(translation("liquidbounce.command.fakeplayer.mustBeInGame")))
        stopRecording()
        return
    }
    if (snapshots.size >= Int.MAX_VALUE - 1) {
        chat(
            markAsError(translation("liquidbounce.command.fakeplayer.recordingForTooLong")),
            metadata = MessageMetadata(id = "CFakePlayer#info"),
        )
        stopRecording()
        return
    }
    snapshots.add(fromPlayerMotion(player))
}

private fun calculateAttackDamage(fakePlayer: LivingEntity): Float {
    var genericDamage = if (player.isAutoSpinAttack) {
        player.autoSpinAttackDmg
    } else {
        player.getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat()
    }
    val damageSource = player.damageSources().playerAttack(player)
    var enchantmentDamage = player.getEnchantedDamage(fakePlayer, genericDamage, damageSource) - genericDamage
    val cooldown = player.getAttackStrengthScale(0.5f)
    genericDamage *= 0.2f + cooldown * cooldown * 0.8f
    enchantmentDamage *= cooldown
    return fakePlayer.getEffectiveDamage(damageSource, genericDamage + enchantmentDamage, false)
}

private fun FakePlayer.applyEstimatedDamage(damage: Float) {
    val result = estimateFakePlayerDamage(health, absorptionAmount, damage)
    absorptionAmount = result.absorption
    health = result.health
}

internal data class FakePlayerDamageState(val health: Float, val absorption: Float)

internal fun estimateFakePlayerDamage(health: Float, absorption: Float, damage: Float): FakePlayerDamageState {
    if (damage <= 0f) {
        return FakePlayerDamageState(health, absorption)
    }
    val absorbedDamage = damage.coerceAtMost(absorption)
    val remainingDamage = damage - absorbedDamage
    return FakePlayerDamageState(
        health = if (remainingDamage > 0f) health - remainingDamage else health,
        absorption = if (absorbedDamage > 0f) (absorption - absorbedDamage).coerceAtLeast(0f) else absorption,
    )
}
