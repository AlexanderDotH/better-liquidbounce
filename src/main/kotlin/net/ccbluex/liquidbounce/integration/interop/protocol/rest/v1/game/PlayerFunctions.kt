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

package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.ccbluex.liquidbounce.common.interop.PlayerDataPayload
import net.ccbluex.liquidbounce.common.interop.PlayerInventoryDataPayload
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSwordBlock.hideShieldSlot
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSwordBlock.shouldHideOffhand
import net.ccbluex.liquidbounce.features.module.modules.misc.nameprotect.ModuleNameProtect
import net.ccbluex.liquidbounce.integration.theme.component.ModernContextualBar
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.entity.armorItems
import net.ccbluex.liquidbounce.utils.entity.getActualHealth
import net.ccbluex.liquidbounce.utils.entity.hasHealthScoreboard
import net.ccbluex.liquidbounce.utils.entity.netherPosition
import net.ccbluex.liquidbounce.utils.entity.ping
import net.ccbluex.liquidbounce.integration.inventory.EnderChestInventoryTracker
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import kotlin.math.min

// GET /api/v1/client/player
private fun Route.getPlayerData() = get {
    val playerData = mc.player?.let(PlayerData::fromPlayer)
    if (playerData != null) {
        call.respond(playerData)
    } else {
        call.respond(io.ktor.http.HttpStatusCode.NoContent)
    }
}

// GET /api/v1/client/player/contextualBar
private fun Route.getContextualBar() = get("/contextualBar") {
    call.respond(ModernContextualBar.snapshot())
}

// GET /api/v1/client/player/inventory
private fun Route.getPlayerInventory() = get("/inventory") {
    val playerInventoryData = mc.player?.let(PlayerInventoryData::fromPlayer)
    if (playerInventoryData != null) {
        call.respond(playerInventoryData)
    } else {
        call.respond(io.ktor.http.HttpStatusCode.NoContent)
    }
}

// GET /api/v1/client/crosshair
private fun Route.getCrosshairData() = get("/crosshair") {
    val crosshairData = mc.hitResult
    if (crosshairData != null) {
        call.respond(crosshairData)
    } else {
        call.respond(io.ktor.http.HttpStatusCode.NoContent)
    }
}

@JvmRecord
data class PlayerData(
    val username: String,
    val uuid: String,
    val dimension: Identifier,
    val position: Vec3,
    val netherPosition: Vec3,
    val blockPosition: BlockPos,
    val velocity: Vec3,
    val selectedSlot: Int,
    val gameMode: GameType = GameType.DEFAULT_MODE,
    val health: Float,
    val actualHealth: Float,
    val maxHealth: Float,
    val absorption: Float,
    val yaw: Float,
    val pitch: Float,
    val armor: Int,
    val food: Int,
    val air: Int,
    val maxAir: Int,
    val experienceLevel: Int,
    val experienceProgress: Float,
    val ping: Int,
    val effects: List<MobEffectInstance>,
    val mainHandStack: ItemStack,
    val offHandStack: ItemStack,
    val armorItems: List<ItemStack> = emptyList(),
    val scoreboard: ScoreboardData? = null,
) : PlayerDataPayload {

    companion object {

        @JvmStatic
        fun fromPlayer(player: Player) = PlayerData(
            ModuleNameProtect.replace(player.scoreboardName),
            player.stringUUID,
            player.level().dimension().identifier(),
            player.position(),
            player.netherPosition,
            player.blockPosition(),
            player.deltaMovement,
            player.inventory.selectedSlot,
            player.gameMode() ?: GameType.DEFAULT_MODE,
            player.health.fixNaN(),
            player.getActualHealth().fixNaN(),
            player.maxHealth.fixNaN(),
            if (player.hasHealthScoreboard()) 0f else player.absorptionAmount.fixNaN(),
            player.yRot.fixNaN(),
            player.xRot.fixNaN(),
            player.armorValue.coerceAtMost(20),
            min(player.foodData.foodLevel, 20),
            player.airSupply,
            player.maxAirSupply,
            player.experienceLevel,
            player.experienceProgress.fixNaN(),
            player.ping,
            player.activeEffects.toList(),
            player.mainHandItem,
            if (player == mc.player && shouldHideOffhand() && hideShieldSlot) ItemStack.EMPTY else player.offhandItem,
            player.armorItems.toList(),
            ScoreboardData.fromScoreboard(
                player.level().scoreboard
            )
        )
    }

}

@JvmRecord
data class PlayerInventoryData(
    val armor: List<ItemStack>,
    val main: List<ItemStack>,
    val crafting: List<ItemStack>,
    val enderChest: List<ItemStack>,
) : PlayerInventoryDataPayload {

    companion object {
        @JvmStatic
        fun fromPlayer(player: Player) = PlayerInventoryData(
            armor = player.armorItems.map(ItemStack::copy),
            main = player.inventory.nonEquipmentItems.map(ItemStack::copy),
            crafting = player.inventoryMenu.craftSlots.items.map(ItemStack::copy),
            /** player.enderChestInventory.getHeldStacks().map(ItemStack::copy) */
            enderChest = EnderChestInventoryTracker.stacks,
        )
    }

}

/**
 * GSON is not happy with NaN values, so we fix them to be 0.
 */
private fun Float.fixNaN() = if (isNaN()) 0f else this

internal fun Route.playerRoutes() {
    route("/player") {
        getPlayerData()
        getContextualBar()
        getPlayerInventory()
    }
    getCrosshairData()
}
