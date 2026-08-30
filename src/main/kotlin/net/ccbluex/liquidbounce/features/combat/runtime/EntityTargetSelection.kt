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

import net.ccbluex.fastutil.component1
import net.ccbluex.fastutil.component2
import net.ccbluex.liquidbounce.features.combat.contract.CombatRuntimeEnvironment
import net.ccbluex.liquidbounce.features.combat.model.EntityTargetClassification
import net.ccbluex.liquidbounce.features.combat.model.EntityTargetingInfo
import net.ccbluex.liquidbounce.features.combat.model.Targets
import net.ccbluex.liquidbounce.features.global.GlobalSettingsTarget
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberRuntime
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.isOlderThanOrEqual1_8
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.world.getEntitiesInCube
import net.minecraft.client.CameraType
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Attackable
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.fish.WaterAnimal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.AbstractArrow

internal fun Set<Targets>.shouldAttack(entity: Entity): Boolean {
    if (entity === player || entity.hasPassenger(player)) {
        return false
    }

    val info = EntityTaggingManager.getTag(entity).targetingInfo

    return when {
        info.isFriend && Targets.FRIENDS !in this -> false
        info.classification === EntityTargetClassification.TARGET -> isInteresting(entity, info)
        else -> false
    }
}

internal fun Set<Targets>.shouldShow(entity: Entity): Boolean {
    if (entity === player || entity.hasPassenger(player)) {
        return Targets.SELF in this &&
            (mc.options.cameraType !== CameraType.FIRST_PERSON || CombatRuntimeEnvironment.isDetachedViewEnabled())
    }

    val info = EntityTaggingManager.getTag(entity).targetingInfo

    return when {
        info.isFriend && Targets.FRIENDS !in this -> false
        info.classification !== EntityTargetClassification.IGNORED -> isInteresting(entity, info)
        else -> false
    }
}

/**
 * Check if an entity is considered a target
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount")
internal fun Set<Targets>.isInteresting(suspect: Entity, info: EntityTargetingInfo): Boolean {
    // Check if the enemy is living and not dead (or ignore being dead)
    if (suspect !is LivingEntity || !(Targets.DEAD in this || suspect.isAlive)) {
        return false
    }

    // Check if enemy is invisible (or ignore being invisible)
    if (Targets.INVISIBLE !in this && suspect.isInvisible) {
        return false
    }

    // Trial membership is a terminal category decision. This keeps Trial and Hostile independent while
    // retaining the Dead, Invisible, friend, and classification safety gates above.
    trialMembershipDecision(TrialChamberRuntime.isCurrentTrialMob(suspect.uuid))?.let { return it }

    // Check if enemy is a player and should be considered as a target
    return when (suspect) {
        is Player -> when {
            suspect === mc.player -> false
            // Check if enemy is sleeping (or ignore being sleeping)
            suspect.isSleeping && Targets.SLEEPING !in this -> false
            // Allow targeting friends even when Players is disabled, as long as Friends is enabled
            else -> Targets.PLAYERS in this || (info.isFriend && Targets.FRIENDS in this)
        }
        is WaterAnimal -> Targets.WATER_CREATURE in this
        is AgeableMob, is Bat, is Allay -> Targets.PASSIVE in this
        is Monster, is Enemy -> Targets.HOSTILE in this
        is NeutralMob -> Targets.ANGERABLE in this

        else -> false
    }
}

/** Null means normal type classification should continue; non-null is the terminal Trial decision. */
internal fun Set<Targets>.trialMembershipDecision(isCurrentTrialMob: Boolean): Boolean? =
    if (isCurrentTrialMob) Targets.TRIAL in this else null

// Extensions
@JvmOverloads
fun Entity?.shouldBeShown(enemyConf: Set<Targets> = GlobalSettingsTarget.visual) =
    this?.let { enemyConf.shouldShow(it) } ?: false

@JvmOverloads
fun Entity?.shouldBeAttacked(enemyConf: Set<Targets> = GlobalSettingsTarget.combat) =
    this is Attackable && enemyConf.shouldAttack(this)

/**
 * Mirrors the vanilla server-side invalid attack disconnect checks
 *
 * @see net.minecraft.server.network.ServerGamePacketListenerImpl.handleAttack
 */
internal fun Entity.canBeAttackedWithVanillaPacket() =
    this !is ItemEntity &&
        this !is ExperienceOrb &&
        this !== player &&
        (this !is AbstractArrow || this.isAttackable)

/**
 * Find the best enemy in the current world in a specific range.
 */
@JvmOverloads
fun ClientLevel.findEnemy(
    range: ClosedFloatingPointRange<Float>,
    enemyConf: Set<Targets> = GlobalSettingsTarget.combat
) = findEnemy(range.start, range.endInclusive, enemyConf)

/**
 * Find the best enemy in the current world in a specific range.
 */
@JvmOverloads
fun ClientLevel.findEnemy(
    minRange: Float,
    maxRange: Float,
    enemyConf: Set<Targets> = GlobalSettingsTarget.combat
) = findEnemies(minRange, maxRange, enemyConf)
    .minByOrNull { (_, distSqr) -> distSqr }?.key()
