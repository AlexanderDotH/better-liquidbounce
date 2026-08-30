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

package net.ccbluex.liquidbounce.features.module.modules.render.skinchanger

import com.mojang.authlib.yggdrasil.YggdrasilEnvironment
import net.ccbluex.liquidbounce.api.core.HttpClient
import net.ccbluex.liquidbounce.api.thirdparty.mojang.service.MinecraftServicesApi
import net.ccbluex.liquidbounce.features.account.accountType
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.player.PlayerModelType
import java.io.IOException

internal object SkinUploadAccess {

    fun canUpload(moduleRunning: Boolean, uploadEnabled: Boolean): Boolean {
        if (!moduleRunning || !uploadEnabled || mc.user.accountType == "legacy") return false

        val sessionService = mc.services.sessionService
        val baseUrl = SkinSessionEndpointBridge.baseUrl(sessionService) ?: return false
        if (baseUrl.startsWith(YggdrasilEnvironment.PROD.environment.sessionHost)) return true

        logger.info("Skipped skin upload as custom authentication endpoint is used: $baseUrl")
        return false
    }

    inline fun request(block: MinecraftServicesApi.() -> Unit) {
        try {
            HttpClient.mojangApiClient.mcServicesApi.block()
        } catch (exception: retrofit2.HttpException) {
            logger.error("Failed to upload skin: ${exception.code()} ${exception.message()}", exception)
        } catch (exception: IOException) {
            logger.error("Failed to upload skin", exception)
        }
    }
}

internal val PlayerModelType.skinVariant: String
    get() = when (this) {
        PlayerModelType.WIDE -> "classic"
        PlayerModelType.SLIM -> "slim"
    }
