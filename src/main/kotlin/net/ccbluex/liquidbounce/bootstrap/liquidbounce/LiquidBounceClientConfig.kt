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
package net.ccbluex.liquidbounce.bootstrap.liquidbounce

import net.ccbluex.liquidbounce.common.ClientBuildMetadata
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.utils.client.logger

internal object LiquidBounceClientConfig : Config("Client") {
    val version = text("Version", ClientBuildMetadata.version).immutable()
    val commit = text("Commit", ClientBuildMetadata.commit).immutable()
    val branch = text("Branch", ClientBuildMetadata.branch).immutable()

    val clientVersion by version
    val clientCommit by commit
    val clientBranch by branch

    init {
        ConfigSystem.root(this)
        version.onChange { previousVersion ->
            runCatching {
                ConfigSystem.backup("automatic_${previousVersion}-${version.inner}")
            }.onFailure {
                logger.error("Unable to create backup", it)
            }
            previousVersion
        }
    }
}
