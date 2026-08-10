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
package net.ccbluex.liquidbounce.utils.network

import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.damagesource.DamageType

/**
 * MappedRegistry.getId is identity-based on the [DamageType] value. Holders taken from a different
 * [RegistryAccess] (e.g. BaseFinder's background WorldLoader stem) fail to encode on the live
 * connection with "Can't find id for … minecraft:spear".
 *
 * Re-resolve by [ResourceKey] against the buffer's registry before writing.
 */
object DamageTypeNetworkRebind {
    @JvmStatic
    fun rebind(registryAccess: RegistryAccess, holder: Holder<DamageType>): Holder<DamageType> {
        val key = holder.unwrapKey().orElse(null) ?: return holder
        val local = registryAccess.lookup(Registries.DAMAGE_TYPE).flatMap { it.get(key) }.orElse(null)
            ?: return holder
        return if (local.value() === holder.value()) holder else local
    }
}
