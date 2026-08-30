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
package net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.runtime

import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.gson.adapter.toUnderlinedString
import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.onClick
import net.ccbluex.liquidbounce.utils.text.onHover
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.text.underline
import net.ccbluex.liquidbounce.utils.text.variable
import net.ccbluex.liquidbounce.utils.text.asText
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import java.io.File
import java.time.LocalDateTime

abstract class DebugRecorderMode<T>(name: String) : Mode(name) {
    final override val parent: ModeValueGroup<*>
        get() = checkNotNull(base as? ModeValueGroup<*>) { "Debug recorder mode is not attached to its parent" }

    protected val owner: ClientModule
        get() = checkNotNull(parent.eventListener as? ClientModule)

    val folder = ConfigSystem.rootFolder.resolve("debug-recorder/$name").apply(File::mkdirs)
    protected val packets = mutableListOf<T>()

    protected fun recordPacket(packet: T) {
        if (isSelected) packets.add(packet)
    }

    protected open val fileExtension: String get() = "json"

    protected open fun writePackets(file: File) {
        file.bufferedWriter().use { writer -> fileGson.toJson(packets, writer) }
    }

    override fun enable() {
        packets.clear()
        chat(regular("Recording "), variable(name), regular("..."))
    }

    override fun disable() {
        if (packets.isEmpty()) {
            chat(regular("No packets recorded."))
            return
        }
        runCatching(::writeCapture).onFailure {
            chat(markAsError("Failed to write log to file $it"))
        }.onSuccess { path ->
            val text = path.asText().underline(true)
                .onHover(HoverEvent.ShowText(regular("Browse...")))
                .onClick(ClickEvent.OpenFile(path))
            chat(regular("Log was written to "), text, regular("."))
        }
        packets.clear()
    }

    private fun writeCapture(): String {
        folder.mkdirs()
        val baseName = LocalDateTime.now().toUnderlinedString()
        var file = folder.resolve("$baseName.$fileExtension")
        var index = 0
        while (file.exists()) file = folder.resolve("${baseName}_${index++}.$fileExtension")
        writePackets(file)
        return file.absolutePath
    }
}
