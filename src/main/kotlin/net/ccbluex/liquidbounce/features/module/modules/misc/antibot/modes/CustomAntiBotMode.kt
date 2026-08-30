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
package net.ccbluex.liquidbounce.features.module.modules.misc.antibot.modes

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import net.ccbluex.fastutil.forEachInt
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.contract.AntiBotPredicate
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.contract.AntiBotProfileBridge
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.custom.CustomAntiBotArmor
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.custom.CustomAntiBotName
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.CRITICAL_MODIFICATION
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.network.protocol.game.ClientboundAnimatePacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import kotlin.math.abs

@Suppress("MagicNumber")
object CustomAntiBotMode : AntiBotMode("Custom") {

    private object InvalidGround : ToggleableValueGroup(null, "InvalidGround", true) {
        val vlToConsiderAsBot by int("VLToConsiderAsBot", 10, 1..50, "flags")
    }

    private val customConditions by multiEnumChoice<CustomConditions>(
        "Conditions",
        CustomConditions.NO_GAME_MODE,
        CustomConditions.ILLEGAL_PITCH,
        CustomConditions.FAKE_ENTITY_ID,
    )

    private object AlwaysInRadius : ToggleableValueGroup(null, "AlwaysInRadius", false) {
        val alwaysInRadiusRange by float("AlwaysInRadiusRange", 20f, 5f..30f)
    }

    private object Age : ToggleableValueGroup(null, "Age", false), AntiBotPredicate {
        private val minimum by int("Minimum", 20, 0..120, "ticks")

        override fun isBot(entity: Player): Boolean = entity.tickCount < minimum
    }

    init {
        tree(InvalidGround)
        tree(AlwaysInRadius)
        tree(Age)
        tree(CustomAntiBotArmor)
        tree(CustomAntiBotName)
    }

    private val flyingSet = Int2IntOpenHashMap()
    private val hitSet = IntOpenHashSet()
    private val notAlwaysInRadiusSet = IntOpenHashSet()

    private val swungSet = IntOpenHashSet()
    private val crittedSet = IntOpenHashSet()
    private val attributesSet = IntOpenHashSet()

    private val armorSet = IntOpenHashSet()

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent>(priority = CRITICAL_MODIFICATION) {
        val rangeSquared = AlwaysInRadius.alwaysInRadiusRange.sq()
        for (entity in world.players()) {
            if (entity === player) {
                continue
            }

            if (player.distanceToSqr(entity) > rangeSquared) {
                notAlwaysInRadiusSet.add(entity.id)
            }

            if (CustomAntiBotArmor.enabled && !CustomAntiBotArmor.isValid(entity)) {
                armorSet.add(entity.id)
            }
        }

        armorSet.removeIf {
            val entity = world.getEntity(it) as? Player
            entity == null || CustomAntiBotArmor.isValid(entity)
        }
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        hitSet.add(event.entity.id)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is ClientboundMoveEntityPacket if packet.hasPosition() && InvalidGround.enabled -> mc.execute {
                val entity = packet.getEntity(world) ?: return@execute
                val id = entity.id
                val currentValue = flyingSet.getOrDefault(id, 0)
                if (entity.onGround() && entity.yo != entity.y) {
                    flyingSet.put(id, currentValue + 1)
                } else if (!entity.onGround() && currentValue > 0) {
                    val newVL = currentValue / 2

                    if (newVL <= 0) {
                        flyingSet.remove(id)
                    } else {
                        flyingSet.put(id, newVL)
                    }
                }
            }

            is ClientboundUpdateAttributesPacket -> mc.execute {
                attributesSet.add(packet.entityId)
            }

            is ClientboundAnimatePacket -> {
                when (packet.action) {
                    ClientboundAnimatePacket.SWING_MAIN_HAND, ClientboundAnimatePacket.SWING_OFF_HAND -> mc.execute {
                        swungSet.add(packet.id)
                    }
                    ClientboundAnimatePacket.CRITICAL_HIT, ClientboundAnimatePacket.MAGIC_CRITICAL_HIT -> mc.execute {
                        crittedSet.add(packet.id)
                    }
                }
            }

            is ClientboundRemoveEntitiesPacket -> mc.execute {
                packet.entityIds.forEachInt { entityId ->
                    attributesSet.remove(entityId)
                    flyingSet.remove(entityId)
                    hitSet.remove(entityId)
                    swungSet.remove(entityId)
                    crittedSet.remove(entityId)
                    notAlwaysInRadiusSet.remove(entityId)
                    armorSet.remove(entityId)
                }
            }
        }
    }

    private fun hasInvalidGround(player: Player): Boolean {
        return flyingSet.getOrDefault(player.id, 0) >= InvalidGround.vlToConsiderAsBot
    }

    override fun isBot(entity: Player): Boolean {
        val entityId = entity.id
        return when {
            InvalidGround.enabled && hasInvalidGround(entity) -> true
            AlwaysInRadius.enabled && !notAlwaysInRadiusSet.contains(entityId) -> true
            Age.enabled && Age.isBot(entity) -> true
            CustomAntiBotArmor.enabled && armorSet.contains(entityId) -> true
            CustomAntiBotName.enabled && CustomAntiBotName.isBot(entity) -> true
            else -> customConditions.any { it.isBot(entity) }
        }
    }

    override fun reset() {
        flyingSet.clear()
        notAlwaysInRadiusSet.clear()
        hitSet.clear()
        swungSet.clear()
        crittedSet.clear()
        attributesSet.clear()
        armorSet.clear()
    }

    @Suppress("unused")
    private enum class CustomConditions(
        override val tag: String,
        private val isBot: AntiBotPredicate
    ) : Tagged, AntiBotPredicate by isBot {
        DUPLICATE("Duplicate", { suspected ->
            AntiBotProfileBridge.isDuplicate(suspected.gameProfile)
        }),
        NO_GAME_MODE("NoGameMode", { suspected ->
            network.getPlayerInfo(suspected.uuid)?.gameMode == null
        }),
        ILLEGAL_PITCH("IllegalPitch", { suspected ->
            abs(suspected.xRot) > 90
        }),
        FAKE_ENTITY_ID("FakeEntityID", { suspected ->
            suspected.id !in 0..1_000_000_000
        }),
        NEED_IT("NeedHit", { suspected ->
            !hitSet.contains(suspected.id)
        }),
        ILLEGAL_HEALTH("IllegalHealth", { suspected ->
            suspected.health > player.maxHealth
        }),
        SWUNG("Swung", { suspected ->
            !swungSet.contains(suspected.id)
        }),
        CRITTED("Critted", { suspected ->
            !crittedSet.contains(suspected.id)
        }),
        ATTRIBUTES("Attributes", { suspected ->
            !attributesSet.contains(suspected.id)
        }),
        ILLEGAL_SCALE("IllegalScale", { suspected ->
            suspected.attributes.getValue(Attributes.SCALE) != 1.0
        })
    }
}
