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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game

import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.common.interop.ScoreboardEntryOrder
import net.ccbluex.liquidbounce.features.module.modules.misc.nameprotect.sanitizeForeignInput
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import net.minecraft.world.scores.Scoreboard

@JvmRecord
data class ScoreboardData(val header: Component, val entries: List<SidebarEntry?>) {

    @JvmRecord
    data class SidebarEntry(val name: Component, val score: Component)

    companion object {

        /**
         * Creates a [ScoreboardData] from the player's scoreboard.
         *
         * @see net.minecraft.client.gui.Hud.extractScoreboardSidebar
         * @see net.minecraft.client.gui.Hud.displayScoreboardSidebar
         */
        @JvmStatic
        fun fromScoreboard(scoreboard: Scoreboard?): ScoreboardData? {
            scoreboard ?: return null
            val objective = scoreboard.sidebarObjective() ?: return null
            val numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)
            val entries = objective.scoreboard.entries(objective, numberFormat)
            return ScoreboardData(objective.displayName.sanitizeForeignInput(), entries)
        }

        private fun Scoreboard.sidebarObjective() = mc.player
            ?.let { getPlayersTeam(it.scoreboardName) }
            ?.color
            ?.orElse(null)
            ?.displaySlot()
            ?.let { getDisplayObjective(it) }
            ?: getDisplayObjective(DisplaySlot.SIDEBAR)

        private fun Scoreboard.entries(
            objective: net.minecraft.world.scores.Objective,
            numberFormat: NumberFormat,
        ): List<SidebarEntry?> = listPlayerScores(objective)
            .filter { score: PlayerScoreEntry -> !score.isHidden }
            .sortedWith(ScoreboardEntryOrder.comparator())
            .take(15)
            .mapToArray { entry -> entry.toSidebarEntry(this, numberFormat) }
            .asList()

        private fun PlayerScoreEntry.toSidebarEntry(
            scoreboard: Scoreboard,
            numberFormat: NumberFormat,
        ): SidebarEntry {
            val team = scoreboard.getPlayersTeam(owner())
            val name = PlayerTeam.formatNameForTeam(team, ownerName()).sanitizeForeignInput()
            return SidebarEntry(name, formatValue(numberFormat).sanitizeForeignInput())
        }
    }
}
