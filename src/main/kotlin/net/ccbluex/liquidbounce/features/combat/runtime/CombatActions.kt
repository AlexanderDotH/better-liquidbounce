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


@file:JvmName("CombatExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.combat.runtime

import it.unimi.dsi.fastutil.objects.ObjectDoubleImmutablePair
import it.unimi.dsi.fastutil.objects.ObjectDoublePair
import net.ccbluex.fastutil.component1
import net.ccbluex.fastutil.component2
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackHook
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.features.combat.contract.CombatRuntimeEnvironment
import net.ccbluex.liquidbounce.features.combat.model.Targets
import net.ccbluex.liquidbounce.features.global.GlobalSettingsTarget
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.world.getEntitiesInCube
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundAttackPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3

@JvmOverloads
fun ClientLevel.findEnemies(
    minRange: Float,
    maxRange: Float,
    enemyConf: Set<Targets> = GlobalSettingsTarget.combat
): List<ObjectDoublePair<Entity>> {
    val minRangeSqr = minRange * minRange
    val maxRangeSqr = maxRange * maxRange
    val result = ArrayList<ObjectDoubleImmutablePair<Entity>>()

    getEntitiesInCube(player.eyePosition, maxRange.toDouble()) {
        it.shouldBeAttacked(enemyConf)
    }.forEach { entity ->
        val distSqr = entity.squaredBoxedDistanceTo(player)
        if (distSqr in minRangeSqr..maxRangeSqr) {
            result += ObjectDoubleImmutablePair(entity, distSqr)
        }
    }

    return result
}

inline fun ClientLevel.getEntitiesBoxInRange(
    midPos: Vec3,
    range: Double,
    crossinline predicate: (Entity) -> Boolean = { true }
): MutableList<Entity> {
    val rangeSquared = range * range

    return getEntitiesInCube(midPos, range) {
        predicate(it) && it.squaredBoxedDistanceTo(midPos) <= rangeSquared
    }
}

/**
 * @see net.minecraft.client.Minecraft.startAttack
 */
fun attackEntity(entity: Entity, swing: SwingMode, keepSprint: Boolean = false) {
    attackEntityWithResult(entity, swing, keepSprint)
}

/**
 * Executes [attackEntity] and reports whether its accepted-attack preparation was applied.
 *
 * [AcceptedAttackResult.REJECTED] also covers an invalid or cancelled attack, so callers that own
 * a remote route never mark an attack as committed when no attack packet was sent.
 */
fun attackEntityWithResult(
    entity: Entity,
    swing: SwingMode,
    keepSprint: Boolean = false,
): AcceptedAttackResult {
    performPiercingAttack(swing)?.let { return it }
    if (isRejectedAttack(entity, keepSprint)) return AcceptedAttackResult.REJECTED
    return commitAcceptedAttack(entity, swing, keepSprint)
}

private fun performPiercingAttack(swing: SwingMode): AcceptedAttackResult? {
    val itemStack = player.getItemInHand(InteractionHand.MAIN_HAND)
    val piercingWeapon = itemStack.get(DataComponents.PIERCING_WEAPON)
    if (piercingWeapon == null || interaction.isSpectator) return null
    interaction.piercingAttack(piercingWeapon)
    swing.swing(InteractionHand.MAIN_HAND)
    return AcceptedAttackResult.NOT_APPLIED
}

private fun isRejectedAttack(entity: Entity, keepSprint: Boolean): Boolean =
    !entity.canBeAttackedWithVanillaPacket() ||
        EventManager.callEvent(AttackEntityEvent(entity, keepSprint)).isCancelled

private fun commitAcceptedAttack(
    entity: Entity,
    swing: SwingMode,
    keepSprint: Boolean,
): AcceptedAttackResult = with(player) {
    if (isOlderThanOrEqual1_8) swing.swing(InteractionHand.MAIN_HAND)
    interaction.ensureHasSentCarriedItem()
    val acceptedAttackResult = AcceptedAttackHook.commit(this, entity)
    if (!acceptedAttackResult.allowsAttack) return@with acceptedAttackResult
    network.send(ServerboundAttackPacket(entity.id))
    if (keepSprint) {
        applyKeepSprintAttackFeedback(entity)
    } else if (interaction.playerMode != GameType.SPECTATOR) {
        attack(entity)
    }
    attackStrengthTicker = 0
    if (!isOlderThanOrEqual1_8) swing.swing(InteractionHand.MAIN_HAND)
    acceptedAttackResult
}

private fun applyKeepSprintAttackFeedback(entity: Entity) {
    return with(player) {
        var genericAttackDamage = if (isAutoSpinAttack) {
            autoSpinAttackDmg
        } else {
            getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat()
        }
        val damageSource = damageSources().playerAttack(this)
        var enchantAttackDamage = getEnchantedDamage(entity, genericAttackDamage, damageSource) - genericAttackDamage
        val attackCooldown = getAttackStrengthScale(0.5f)
        genericAttackDamage *= 0.2f + attackCooldown * attackCooldown * 0.8f
        enchantAttackDamage *= attackCooldown
        if (genericAttackDamage <= 0.0f && enchantAttackDamage <= 0.0f) return@with
        if (enchantAttackDamage > 0.0f) magicCrit(entity)
        if (CombatRuntimeEnvironment.wouldDoCriticalHit(true)) {
            world.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, soundSource, 1.0f, 1.0f)
            crit(entity)
        }
    }
}
