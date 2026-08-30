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

package net.ccbluex.liquidbounce.config

import net.ccbluex.liquidbounce.config.types.Config
import net.ccbluex.liquidbounce.utils.client.clientLogger
import net.ccbluex.liquidbounce.utils.io.createZipArchive
import net.ccbluex.liquidbounce.utils.io.extractZip
import java.io.File

internal object ConfigFileStorage {

    private val logger = clientLogger("ConfigSystem")

    fun backup(backupFolder: File, fileName: String, groups: Iterable<Config>) {
        var zipFile = File(backupFolder, "$fileName.zip")
        var suffix = 1
        while (zipFile.exists()) {
            zipFile = File(backupFolder, "${fileName}_${suffix++}.zip")
        }

        groups.map { valueGroup -> valueGroup.jsonFile }.createZipArchive(zipFile)
    }

    fun restore(
        backupFolder: File,
        rootFolder: File,
        fileName: String,
        configs: Iterable<Config>,
    ) {
        val zipFile = File(backupFolder, "$fileName.zip")
        check(zipFile.exists()) { "Backup file does not exist" }

        configs.forEach(::store)
        extractZip(zipFile, rootFolder)
        configs.forEach(::load)
    }

    fun load(config: Config) {
        config.jsonFile.runCatching {
            if (!exists()) {
                return@runCatching
            }

            logger.debug("Reading config ${config.loweredName}...")
            ConfigValueGroupCodec.deserialize(config, bufferedReader())
        }.onSuccess {
            logger.info("Successfully loaded config '${config.loweredName}'.")
        }.onFailure {
            logger.error("Unable to load config ${config.loweredName}", it)
        }

        store(config)
    }

    fun store(config: Config) {
        config.jsonTmpFile.runCatching {
            logger.debug("Writing config ${config.loweredName}...")
            if (!exists()) {
                createNewFile().let { logger.debug("Created new file (status: $it)") }
            }
            ConfigValueGroupCodec.serialize(config, bufferedWriter())
            logger.debug("Writing config ${config.loweredName}... done")

            if (config.jsonFile.exists() && !config.jsonFile.delete()) {
                error("Unable to delete old file for config ${config.loweredName}")
            }
            if (!renameTo(config.jsonFile)) {
                error("Unable to rename temp file to final file for config ${config.loweredName}")
            }
            logger.info("Successfully stored config '${config.loweredName}'.")
        }.onFailure {
            logger.error("Unable to store config ${config.loweredName}", it)
        }
    }
}
