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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import net.minecraft.core.component.DataComponentMap
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

internal enum class SafeDropSource {
    WORLD,
    CONTAINER,
}

internal class SafeDropScope private constructor(
    private val references: List<Any>,
) {

    override fun equals(other: Any?): Boolean {
        return other is SafeDropScope &&
            references.size == other.references.size &&
            references.indices.all { index -> references[index] === other.references[index] }
    }

    override fun hashCode(): Int = references.fold(1) { hash, reference ->
        31 * hash + System.identityHashCode(reference)
    }

    companion object {
        fun world(world: Any) = SafeDropScope(listOf(world))

        fun container(screen: Any, menu: Any) = SafeDropScope(listOf(screen, menu))
    }
}

internal class SafeDropItemSignature private constructor(
    val item: Item,
    val components: DataComponentMap,
    val count: Int,
) {

    override fun equals(other: Any?): Boolean {
        return other is SafeDropItemSignature &&
            item === other.item &&
            components == other.components &&
            count == other.count
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(item)
        result = 31 * result + components.hashCode()
        return 31 * result + count
    }

    companion object {
        fun capture(stack: ItemStack) = SafeDropItemSignature(
            item = stack.item,
            components = stack.immutableComponents(),
            count = stack.count,
        )
    }
}

internal data class SafeDropContext(
    val source: SafeDropSource,
    val scope: SafeDropScope,
    val slot: Int,
    val item: SafeDropItemSignature,
)

internal data class SafeDropAction(
    val context: SafeDropContext,
    val dropAll: Boolean,
) {

    companion object {
        fun world(world: Any, slot: Int, stack: ItemStack, dropAll: Boolean) = SafeDropAction(
            context = SafeDropContext(
                source = SafeDropSource.WORLD,
                scope = SafeDropScope.world(world),
                slot = slot,
                item = SafeDropItemSignature.capture(stack),
            ),
            dropAll = dropAll,
        )

        fun container(screen: Any, menu: Any, slot: Int, stack: ItemStack, dropAll: Boolean) = SafeDropAction(
            context = SafeDropContext(
                source = SafeDropSource.CONTAINER,
                scope = SafeDropScope.container(screen, menu),
                slot = slot,
                item = SafeDropItemSignature.capture(stack),
            ),
            dropAll = dropAll,
        )
    }
}
