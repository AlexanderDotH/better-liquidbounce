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

package net.ccbluex.liquidbounce.features.module.modules.render.xray

import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.ccbluex.liquidbounce.utils.kotlin.addAll
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.util.SortedSet

private val ORE_BLOCKS = arrayOf(
    Blocks.COAL_ORE,
    Blocks.COPPER_ORE,
    Blocks.DIAMOND_ORE,
    Blocks.EMERALD_ORE,
    Blocks.GOLD_ORE,
    Blocks.IRON_ORE,
    Blocks.LAPIS_ORE,
    Blocks.REDSTONE_ORE,
    Blocks.DEEPSLATE_COAL_ORE,
    Blocks.DEEPSLATE_COPPER_ORE,
    Blocks.DEEPSLATE_DIAMOND_ORE,
    Blocks.DEEPSLATE_EMERALD_ORE,
    Blocks.DEEPSLATE_GOLD_ORE,
    Blocks.DEEPSLATE_IRON_ORE,
    Blocks.DEEPSLATE_LAPIS_ORE,
    Blocks.DEEPSLATE_REDSTONE_ORE,
    Blocks.COAL_BLOCK,
    Blocks.DIAMOND_BLOCK,
    Blocks.EMERALD_BLOCK,
    Blocks.GOLD_BLOCK,
    Blocks.IRON_BLOCK,
    Blocks.LAPIS_BLOCK,
    Blocks.REDSTONE_BLOCK,
    Blocks.RAW_COPPER_BLOCK,
    Blocks.RAW_GOLD_BLOCK,
    Blocks.RAW_IRON_BLOCK,
    Blocks.ANCIENT_DEBRIS,
    Blocks.NETHER_GOLD_ORE,
    Blocks.NETHER_QUARTZ_ORE,
    Blocks.NETHERITE_BLOCK,
    Blocks.QUARTZ_BLOCK,
)

private val CONTAINER_AND_UTILITY_BLOCKS = arrayOf(
    Blocks.CHEST,
    Blocks.DISPENSER,
    Blocks.DROPPER,
    Blocks.ENDER_CHEST,
    Blocks.HOPPER,
    Blocks.TRAPPED_CHEST,
    Blocks.SHULKER_BOX,
    Blocks.BEACON,
    Blocks.CRAFTING_TABLE,
    Blocks.ENCHANTING_TABLE,
    Blocks.FURNACE,
    Blocks.FLOWER_POT,
    Blocks.JUKEBOX,
    Blocks.LODESTONE,
    Blocks.RESPAWN_ANCHOR,
    Blocks.ANVIL,
    Blocks.CHIPPED_ANVIL,
    Blocks.DAMAGED_ANVIL,
    Blocks.BARREL,
    Blocks.BLAST_FURNACE,
    Blocks.BREWING_STAND,
    Blocks.CARTOGRAPHY_TABLE,
    Blocks.COMPOSTER,
    Blocks.FLETCHING_TABLE,
    Blocks.GRINDSTONE,
    Blocks.LECTERN,
    Blocks.LOOM,
    Blocks.SMITHING_TABLE,
    Blocks.SMOKER,
    Blocks.STONECUTTER,
    Blocks.CAULDRON,
    Blocks.LAVA_CAULDRON,
    Blocks.WATER_CAULDRON,
)

private val SPECIAL_BLOCKS = arrayOf(
    Blocks.LAVA,
    Blocks.WATER,
    Blocks.END_PORTAL,
    Blocks.END_PORTAL_FRAME,
    Blocks.NETHER_PORTAL,
    Blocks.CHAIN_COMMAND_BLOCK,
    Blocks.COMMAND_BLOCK,
    Blocks.REPEATING_COMMAND_BLOCK,
    Blocks.BOOKSHELF,
    Blocks.CLAY,
    Blocks.DRAGON_EGG,
    Blocks.FIRE,
    Blocks.SPAWNER,
    Blocks.TNT,
)

internal fun defaultXRayBlocks(): SortedSet<Block> = blockSortedSetOf(
    *ORE_BLOCKS,
    *CONTAINER_AND_UTILITY_BLOCKS,
    *SPECIAL_BLOCKS,
).apply {
    addAll(Blocks.COPPER_BLOCK)
    addAll(Blocks.DYED_SHULKER_BOX)
    addAll(Blocks.COPPER_CHEST)
}
