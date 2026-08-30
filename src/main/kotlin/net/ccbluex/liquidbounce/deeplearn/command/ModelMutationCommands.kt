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
package net.ccbluex.liquidbounce.deeplearn.command

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.deeplearn.DeepLearningEngine.modelsFolder
import net.ccbluex.liquidbounce.deeplearn.ModelManager
import net.ccbluex.liquidbounce.deeplearn.ModelManager.models
import net.ccbluex.liquidbounce.features.command.Command
import net.ccbluex.liquidbounce.features.command.CommandException
import net.ccbluex.liquidbounce.features.command.CommandRuntime.suspendHandler
import net.ccbluex.liquidbounce.features.command.builder.CommandBuilder
import net.ccbluex.liquidbounce.features.command.builder.ParameterBuilder
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.clickablePath
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.regular
import net.minecraft.util.Util

internal fun createModelCommand(mutex: Mutex) = CommandBuilder.begin("create")
    .parameter(modelNameParameter())
    .suspendHandler {
        mutex.withLock {
            val name = args[0] as String
            requireAvailableModelName(command, name)
            chat(command.result("trainingStart", name))
            withContext(Dispatchers.Default) { trainModel(command, name) }
        }
    }
    .build()

internal fun improveModelCommand(mutex: Mutex) = CommandBuilder.begin("improve")
    .parameter(modelNameParameter())
    .suspendHandler {
        mutex.withLock {
            val name = args[0] as String
            val model = models.modes.find { it.name.equals(name, true) }
                ?: throw CommandException(command.result("modelNotFound", name))
            chat(command.result("trainingStart", name))
            withContext(Dispatchers.Default) { trainModel(command, name, model) }
        }
    }
    .build()

internal fun deleteModelCommand(mutex: Mutex) = CommandBuilder.begin("delete")
    .parameter(modelNameParameter())
    .suspendHandler {
        mutex.withLock { deleteModel(command, args[0] as String) }
    }
    .build()

internal fun reloadModelCommand(mutex: Mutex) = CommandBuilder.begin("reload")
    .suspendHandler {
        mutex.withLock { ModelManager.reload() }
        chat(command.result("modelsReloaded"))
    }
    .build()

internal fun browseModelCommand() = CommandBuilder.begin("browse")
    .handler {
        Util.getPlatform().openFile(modelsFolder)
        chat(regular("Location: "), clickablePath(modelsFolder))
    }
    .build()

private fun modelNameParameter() = ParameterBuilder.begin<String>("name").required().build()

private fun requireAvailableModelName(command: Command, name: String) {
    if (models.modes.any { model -> model.name.equals(name, true) }) {
        throw CommandException(command.result("modelExists", name))
    }
    if (name.contains(Regex("[^a-zA-Z0-9-]"))) {
        throw CommandException(command.result("invalidName"))
    }
}

private suspend fun deleteModel(command: Command, name: String) {
    val model = models.modes.find { candidate ->
        candidate.name.equals(name, true) && modelsFolder.resolve(candidate.name).isDirectory
    }
    if (model == null) {
        chat(markAsError(command.result("modelNotFound", name)))
        return
    }
    val deleted = withContext(Dispatchers.IO) {
        runCatching { model.delete() }
            .onFailure { error -> reportDeleteFailure(command, name, error) }
            .isSuccess
    }
    if (!deleted) {
        restoreModelsAfterDeleteFailure(name)
        return
    }
    if (!reloadModelsAfterDelete(command, name)) {
        return
    }
    chat(command.result("modelDeleted", name))
}

private fun reportDeleteFailure(command: Command, name: String, error: Throwable) {
    logger.error("Failed to delete model '$name'.", error)
    chat(markAsError(command.result("modelDeleteFailed", name, error.localizedMessage)))
}

private suspend fun restoreModelsAfterDeleteFailure(name: String) {
    runCatching { ModelManager.reload() }.onFailure { error ->
        logger.error("Failed to restore models after deleting '$name' failed.", error)
    }
}

private suspend fun reloadModelsAfterDelete(command: Command, name: String): Boolean =
    runCatching { ModelManager.reload() }
        .onFailure { error ->
            logger.error("Failed to reload models after deleting '$name'.", error)
            chat(markAsError(command.result("modelDeleteFailed", name, error.localizedMessage)))
        }
        .isSuccess
