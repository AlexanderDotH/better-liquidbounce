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
package net.ccbluex.liquidbounce.utils.entity

import net.minecraft.core.BlockPos
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LivingDamageReducerTest {

    @Test
    fun `damage defenses retain vanilla evaluation order`() {
        val access = RecordingDamageAccess(
            blockedDamage = 2.0F,
            armorReduction = { it * 0.5F },
            magicReduction = { it - 1.0F },
            absorption = 3.0F,
        )

        val damage = LivingDamageReducer.reduce(
            access = access,
            damage = 20.0F,
            ignoreShield = false,
            includeAbsorption = true,
        )

        assertEquals(5.0F, damage)
        assertEquals(
            listOf(
                "invulnerable",
                "deadOrDying",
                "player",
                "fireDamage",
                "blockedDamage:20.0",
                "armor:18.0",
                "magic:9.0",
                "absorption",
            ),
            access.calls,
        )
    }

    @Test
    fun `invulnerability exits before querying later state`() {
        val access = RecordingDamageAccess(invulnerable = true)

        assertEquals(0.0F, LivingDamageReducer.reduce(access, 20.0F))
        assertEquals(listOf("invulnerable"), access.calls)
    }

    @Test
    fun `player difficulty scaling retains exact float arithmetic`() {
        val easy = RecordingDamageAccess(player = true, difficulty = Difficulty.EASY)
        val hard = RecordingDamageAccess(player = true, difficulty = Difficulty.HARD)
        val peaceful = RecordingDamageAccess(player = true, difficulty = Difficulty.PEACEFUL)

        assertEquals(6.0F, LivingDamageReducer.reduce(easy, 10.0F, ignoreShield = true))
        assertEquals(15.0F, LivingDamageReducer.reduce(hard, 10.0F, ignoreShield = true))
        assertEquals(0.0F, LivingDamageReducer.reduce(peaceful, 10.0F, ignoreShield = true))
        assertEquals("difficulty", easy.calls[5])
        assertEquals("difficulty", hard.calls[5])
        assertEquals(
            listOf(
                "invulnerable",
                "deadOrDying",
                "player",
                "playerInvulnerable",
                "scalesWithDifficulty",
                "difficulty",
            ),
            peaceful.calls,
        )
    }

    @Test
    fun `fire resistance exits before shield armor and magic`() {
        val access = RecordingDamageAccess(fireDamage = true, fireResistance = true)

        assertEquals(0.0F, LivingDamageReducer.reduce(access, 20.0F))
        assertEquals(
            listOf("invulnerable", "deadOrDying", "player", "fireDamage", "fireResistance"),
            access.calls,
        )
    }

    @Test
    fun `public damage extension descriptors remain compatible`() {
        val facade = Class.forName("net.ccbluex.liquidbounce.utils.entity.EntityExtensionsKt")

        assertNotNull(
            facade.getMethod(
                "getEffectiveDamage",
                LivingEntity::class.java,
                DamageSource::class.java,
                java.lang.Float.TYPE,
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE,
            ),
        )
        assertNotNull(
            facade.getMethod(
                "getEffectiveDamage",
                LivingEntity::class.java,
                DamageSource::class.java,
                java.lang.Float.TYPE,
                java.lang.Boolean.TYPE,
            ),
        )
        assertNotNull(
            facade.getMethod(
                "getEffectiveDamage",
                LivingEntity::class.java,
                DamageSource::class.java,
                java.lang.Float.TYPE,
            ),
        )
        assertNotNull(
            facade.getMethod("getExplosionDamageFromEntity", LivingEntity::class.java, Entity::class.java),
        )
    }

    @Test
    fun `public explosion extension descriptors remain compatible`() {
        val facade = Class.forName("net.ccbluex.liquidbounce.utils.entity.EntityExtensionsKt")

        assertNotNull(
            facade.getMethod(
                "getDamageFromExplosion",
                LivingEntity::class.java,
                Vec3::class.java,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                java.lang.Float.TYPE,
                Collection::class.java,
                BlockPos::class.java,
                Float::class.javaObjectType,
                AABB::class.java,
                DamageSource::class.java,
            ),
        )
        assertNotNull(
            facade.getMethod(
                "getExposureToExplosion",
                LivingEntity::class.java,
                Vec3::class.java,
                Collection::class.java,
                BlockPos::class.java,
                Float::class.javaObjectType,
                AABB::class.java,
            ),
        )
    }

    private class RecordingDamageAccess(
        private val invulnerable: Boolean = false,
        private val deadOrDying: Boolean = false,
        private val player: Boolean = false,
        private val playerInvulnerable: Boolean = false,
        private val bypassesInvulnerability: Boolean = false,
        private val scalesWithDifficulty: Boolean = true,
        private val difficulty: Difficulty = Difficulty.NORMAL,
        private val fireDamage: Boolean = false,
        private val fireResistance: Boolean = false,
        private val blockedDamage: Float = 0.0F,
        private val armorReduction: (Float) -> Float = { it },
        private val magicReduction: (Float) -> Float = { it },
        private val absorption: Float = 0.0F,
    ) : LivingDamageReductionAccess {

        val calls = mutableListOf<String>()

        override fun isInvulnerable() = record("invulnerable", invulnerable)

        override fun isDeadOrDying() = record("deadOrDying", deadOrDying)

        override fun isPlayer() = record("player", player)

        override fun isPlayerInvulnerable() = record("playerInvulnerable", playerInvulnerable)

        override fun bypassesInvulnerability() = record("bypassesInvulnerability", bypassesInvulnerability)

        override fun scalesWithDifficulty() = record("scalesWithDifficulty", scalesWithDifficulty)

        override fun difficulty() = record("difficulty", difficulty)

        override fun isFireDamage() = record("fireDamage", fireDamage)

        override fun hasFireResistance() = record("fireResistance", fireResistance)

        override fun blockedDamage(amount: Float): Float {
            calls += "blockedDamage:$amount"
            return blockedDamage
        }

        override fun afterArmorAbsorb(amount: Float): Float {
            calls += "armor:$amount"
            return armorReduction(amount)
        }

        override fun afterMagicAbsorb(amount: Float): Float {
            calls += "magic:$amount"
            return magicReduction(amount)
        }

        override fun absorptionAmount() = record("absorption", absorption)

        private fun <T> record(name: String, value: T): T {
            calls += name
            return value
        }
    }
}
