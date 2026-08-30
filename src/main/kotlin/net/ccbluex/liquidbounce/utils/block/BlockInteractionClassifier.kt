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
package net.ccbluex.liquidbounce.utils.block

import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.level.block.AbstractChestBlock
import net.minecraft.world.level.block.AbstractFurnaceBlock
import net.minecraft.world.level.block.AnvilBlock
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.block.BeaconBlock
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.BellBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BrewingStandBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.CakeBlock
import net.minecraft.world.level.block.CandleCakeBlock
import net.minecraft.world.level.block.CartographyTableBlock
import net.minecraft.world.level.block.CaveVines
import net.minecraft.world.level.block.CaveVinesBlock
import net.minecraft.world.level.block.CaveVinesPlantBlock
import net.minecraft.world.level.block.ComparatorBlock
import net.minecraft.world.level.block.ComposterBlock
import net.minecraft.world.level.block.CrafterBlock
import net.minecraft.world.level.block.CraftingTableBlock
import net.minecraft.world.level.block.DaylightDetectorBlock
import net.minecraft.world.level.block.DecoratedPotBlock
import net.minecraft.world.level.block.DispenserBlock
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.DragonEggBlock
import net.minecraft.world.level.block.EnchantingTableBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.GameMasterBlock
import net.minecraft.world.level.block.GrindstoneBlock
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.LecternBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.LightBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.RedStoneWireBlock
import net.minecraft.world.level.block.RepeaterBlock
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.StonecutterBlock
import net.minecraft.world.level.block.SweetBerryBushBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState

internal object BlockInteractionClassifier {

    fun isInteractable(block: Block?, blockState: BlockState?): Boolean {
        block ?: return false
        return when {
            block.isEarlyInteraction() -> true
            block.isFoodOrGrowthInteraction(blockState) -> true
            block.isWorkstationOrMechanismInteraction(blockState) -> true
            block.isLateInteraction(blockState) -> true
            else -> false
        }
    }

    private fun Block.isEarlyInteraction(): Boolean = when (this) {
        is BedBlock, is AbstractChestBlock<*>, is AbstractFurnaceBlock, is AnvilBlock,
        is BarrelBlock, is BeaconBlock, is BellBlock, is BrewingStandBlock, is ButtonBlock -> true
        else -> false
    }

    private fun Block.isFoodOrGrowthInteraction(blockState: BlockState?): Boolean = when {
        this is CakeBlock && player.foodData.needsFood() -> true
        this is CandleCakeBlock -> true
        this is CartographyTableBlock -> true
        this is CaveVinesPlantBlock && (blockState?.getValue(CaveVines.BERRIES) ?: true) -> true
        this is CaveVinesBlock && (blockState?.getValue(CaveVines.BERRIES) ?: true) -> true
        else -> false
    }

    private fun Block.isWorkstationOrMechanismInteraction(blockState: BlockState?): Boolean = when {
        this is ComparatorBlock -> true
        this is ComposterBlock && (blockState?.getValue(ComposterBlock.LEVEL) ?: 8) == 8 -> true
        isCraftingOrStorageInteraction() -> true
        this is GameMasterBlock && player.canUseGameMasterBlocks() -> true
        this is JukeboxBlock && blockState?.getValue(JukeboxBlock.HAS_RECORD) == true -> true
        isLecternOrLeverInteraction() -> true
        this is LightBlock && player.canUseGameMasterBlocks() -> true
        isRedstoneOrUtilityInteraction() -> true
        else -> false
    }

    private fun Block.isCraftingOrStorageInteraction(): Boolean = when (this) {
        is CrafterBlock, is CraftingTableBlock, is DaylightDetectorBlock, is DecoratedPotBlock,
        is DispenserBlock, is DoorBlock, is DragonEggBlock, is EnchantingTableBlock, is FenceGateBlock,
        is FlowerPotBlock, is GrindstoneBlock, is HopperBlock -> true
        else -> false
    }

    private fun Block.isLecternOrLeverInteraction(): Boolean = when (this) {
        is LecternBlock, is LeverBlock -> true
        else -> false
    }

    private fun Block.isRedstoneOrUtilityInteraction(): Boolean = when (this) {
        is NoteBlock, is RedStoneWireBlock, is RepeaterBlock, is RespawnAnchorBlock,
        is ShulkerBoxBlock, is StonecutterBlock -> true
        else -> false
    }

    private fun Block.isLateInteraction(blockState: BlockState?): Boolean = when {
        this is SweetBerryBushBlock && (blockState?.getValue(SweetBerryBushBlock.AGE) ?: 2) > 1 -> true
        this is TrapDoorBlock -> true
        else -> false
    }
}
