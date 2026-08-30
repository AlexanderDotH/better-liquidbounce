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

package net.ccbluex.liquidbounce.features.cosmetic

import com.mojang.authlib.GameProfile
import net.ccbluex.liquidbounce.api.models.cosmetics.CosmeticCategory
import net.minecraft.resources.Identifier
import java.util.UUID
import java.util.function.Consumer

object CosmeticRenderHook {
    @JvmStatic
    fun hasDinnerbone(uuid: UUID) = CosmeticService.hasCosmetic(uuid, CosmeticCategory.DINNERBONE)

    @JvmStatic
    fun hasDeadmau5Ears(uuid: UUID) = CosmeticService.hasCosmetic(uuid, CosmeticCategory.DEADMAU5_EARS)

    @JvmStatic
    fun loadPlayerCape(profile: GameProfile, consumer: Consumer<Identifier>) {
        CapeCosmeticsManager.loadPlayerCape(profile, consumer::accept)
    }
}
