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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.InstrumentNote
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.NbsLoader
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.NbsNoteBlock
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.SongData
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.resolveInstrumentNote
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.nbs.resolveRequiredInstruments
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeBridge
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotRuntimeHook
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.contract.NotebotStage
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NoteBlockTracker
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.runtime.NotebotEngine
import net.ccbluex.liquidbounce.features.module.modules.`fun`.notebot.stages.NotebotTestStageHandler
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.ModulePacketMine
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.features.chat.MessageMetadata
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.ccbluex.liquidbounce.utils.text.regular
import net.ccbluex.liquidbounce.utils.client.removeMessage
import net.ccbluex.liquidbounce.utils.text.textLoadingBar
import net.ccbluex.liquidbounce.utils.text.variable
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument

/**
 * Notebot Module
 *
 * Automatically plays note block songs from NBS files.
 *
 * @author ccetl
 */
object ModuleNotebot : ClientModule("Notebot", ModuleCategories.FUN, disableOnQuit = true) {

    private val song = file("Song", supportedExtensions = setOf("nbs"))
    private val pianoOnly by boolean("PianoOnly", false)
    val reuseBlocks by boolean("ReuseBlocks", true).onChanged { enabled = false }
    val range by float("Range", 6f, 1f..6f)
    val rotations = RotationsValueGroup(this)
    val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)

    private object StartDelay : ValueGroup("StartDelay") {
        val test by int("Test", 0, 0..20, "ticks")
        val tune by int("Tune", 0, 0..20, "ticks")
        val play by int("Play", 2, 0..20, "ticks")
    }

    init {
        tree(StartDelay)
    }

    val renderer = tree(NotebotRenderer)

    init {
        NotebotRuntimeBridge.install(object : NotebotRuntimeHook {
            override fun range() = this@ModuleNotebot.range.toDouble()
            override fun reuseBlocks() = this@ModuleNotebot.reuseBlocks
            override fun pianoOnly() = this@ModuleNotebot.pianoOnly
            override fun stageDelay(stage: NotebotStage) = when (stage) {
                NotebotStage.TEST -> StartDelay.test
                NotebotStage.TUNE -> StartDelay.tune
                NotebotStage.PLAY -> StartDelay.play
            }
            override fun onStageChanged(stage: NotebotStage) = renderer.onStateChange(stage)
            override fun message(key: String) = this@ModuleNotebot.message(key)
            override fun chat(component: Component) = chat(component, this@ModuleNotebot)
            override fun reportLoadError(key: String, vararg args: Any) {
                chat(markAsError(this@ModuleNotebot.message(key, *args)), this@ModuleNotebot)
            }
            override fun sendProgress(name: Component, progress: Int, total: Int) =
                sendNewProgressMessage(name.copy(), progress, total)
            override fun setRenderedBlocks(blocks: List<BlockPos>) = setRenderedBlockPositions(blocks)
            override fun disable() {
                enabled = false
            }
        })
    }

    var engine: NotebotEngine? = null
        private set

    @Suppress("unused")
    private val tickHandler = tickHandler {
        engine?.onTick()
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        if (event.packet is ClientboundSoundPacket) {
            this.engine?.handleSoundPacket(event.packet)
        }
    }

    override suspend fun enabledEffect() {
        val messageMetadata = MessageMetadata(id = "M${this.name}#loaded", remove = false)
        mc.gui.hud.chat.removeMessage(messageMetadata.id)

        if (!checkRequirements()) {
            this.enabled = false
            return
        }

        val songData = loadSongData()

        if (songData == null) {
            this.enabled = false
            return
        }

        val blocksAndRequirements = NotebotScanner.scanBlocksAndCheckRequirements(songData)

        if (!blocksAndRequirements.validateRequirements()) {
            blocksAndRequirements.printRequirements()
            this.enabled = false
            return
        }

        setRenderedBlockPositions(blocksAndRequirements.availableBlocks.flatMap { it.value }.map(NoteBlockTracker::pos))

        showSongInfo(songData, messageMetadata)

        this.engine = NotebotEngine(songData, blocksAndRequirements, ::NotebotTestStageHandler)
        chat(message("startTesting").withStyle(ChatFormatting.GREEN), this)
    }

    private fun setRenderedBlockPositions(blocks: List<BlockPos>) {
        renderer.clearSilently()

        blocks.forEach {
            renderer.addBlock(it, false)
        }

        renderer.updateAll()
    }

    private suspend fun loadSongData(): SongData? {
        chat(message("startLoading").withStyle(ChatFormatting.GREEN), this)

        val songData = withContext(Dispatchers.IO) {
            NbsLoader.load(song.absoluteFile)
        }

        return songData
    }

    private fun checkRequirements(): Boolean {
        return when {
            !inGame -> {
                chat(markAsError(message("notInGame")), this)
                false
            }

            player.isCreative -> {
                chat(markAsError(message("inCreative")), this)
                false
            }

            ModulePacketMine.enabled -> {
                chat(markAsError(message("packetMineEnabled")), this)
                false
            }

            else -> true
        }
    }

    private fun showSongInfo(
        songData: SongData,
        messageMetadata: MessageMetadata
    ) {
        chat(
            regular(message("songInfoName", variable(songData.name))),
            messageMetadata
        )
        chat(
            regular(message("songInfoTicksPerGameTick", variable(songData.songTicksPerGameTick.toString()))),
            messageMetadata
        )
        chat(
            regular(message("songInfoTickLength", variable(songData.songTickLength.toString()))),
            messageMetadata
        )
        chat(
            regular(message("songInfoTotalNotes", variable(songData.nbs.noteBlocks.size.toString()))),
            messageMetadata
        )
    }

    override fun onDisabled() {
        removeProgressMessage()

        renderer.reset()
    }

    private val progressMessageMetadata = MessageMetadata(id = "M$name#progress", remove = false)

    private fun removeProgressMessage() {
        mc.gui.hud.chat.removeMessage(progressMessageMetadata.id)
    }

    fun sendNewProgressMessage(name: MutableComponent, progress: Int, total: Int) {
        removeProgressMessage()

        val percent = (progress.toDouble() / total.toDouble() * 100.0).toInt()
        chat(
            variable(name.copy())
                .append(regular(" ["))
                .append(textLoadingBar(percent))
                .append(regular("] "))
                .append(variable(percent.toString()))
                .append(regular("%")),
            metadata = progressMessageMetadata
        )
    }

    fun getPlayedNote(note: NbsNoteBlock): InstrumentNote = resolveInstrumentNote(note, pianoOnly)

    fun getRequiredInstruments(songData: SongData): Set<NoteBlockInstrument> =
        resolveRequiredInstruments(songData, pianoOnly)

}
