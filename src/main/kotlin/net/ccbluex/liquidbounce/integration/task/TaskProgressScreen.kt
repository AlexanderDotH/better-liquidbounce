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

package net.ccbluex.liquidbounce.integration.task

import net.ccbluex.liquidbounce.integration.backend.BrowserBackendManager
import net.ccbluex.liquidbounce.integration.backend.isBrowserDisabled
import net.ccbluex.liquidbounce.common.task.ResourceTask
import net.ccbluex.liquidbounce.common.task.Task
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.collection.Pools
import net.ccbluex.liquidbounce.utils.text.PlainText
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.formatAsCapacity
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import java.text.DecimalFormat

/**
 * Screen that displays TaskManager progress
 */
class TaskProgressScreen(
    title: String,
    private val taskManager: TaskManager
) : Screen(title.asPlainText()) {

    private val percentFormat = DecimalFormat("0.0")

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractMenuBackground(context)
        val cx = width / 2.0
        val cy = height / 2.0
        val progressBarWidth = width / 1.5
        val progress = taskManager.progress
        val textLines = getTaskLines(progress)
        val progressBarY = drawProgressText(context, cx, cy, textLines)
        drawProgressBar(context, cx, progressBarY, progressBarWidth, progress)
    }

    private fun drawProgressText(
        context: GuiGraphicsExtractor,
        centerX: Double,
        centerY: Double,
        textLines: List<Component>,
    ): Int {
        val textHeight = textLines.size * (font.lineHeight + 2)
        var yOffset = (centerY - textHeight / 2).toInt() - 40
        context.text(
            font,
            title.string.asPlainText(ChatFormatting.GOLD),
            (centerX - font.width(title.string) / 2).toInt(),
            yOffset,
            -1,
            true,
        )
        yOffset += font.lineHeight + 10
        for (line in textLines) {
            context.text(font, line, (centerX - font.width(line) / 2).toInt(), yOffset, -1, false)
            yOffset += font.lineHeight + 2
        }
        return yOffset
    }

    private fun drawProgressBar(
        context: GuiGraphicsExtractor,
        centerX: Double,
        yOffset: Int,
        progressBarWidth: Double,
        progress: Float,
    ) {
        val progressBarHeight = 14
        val poseStack = context.pose()
        poseStack.pushMatrix()
        poseStack.translate(centerX.toFloat(), yOffset.toFloat() + 18.0f)
        poseStack.translate(progressBarWidth.toFloat() * -0.5f, progressBarHeight.toFloat() * -0.5f)
        context.fill(0, 0, progressBarWidth.toInt(), progressBarHeight, -1)
        context.fill(
            2, 2,
            (progressBarWidth - 2).toInt(), (progressBarHeight - 2).toInt(),
            ARGB.color(255, 24, 26, 27),
        )
        context.fill(
            4, 4,
            ((progressBarWidth - 4) * progress).toInt(), (progressBarHeight - 4).toInt(),
            -1,
        )
        poseStack.popMatrix()
    }

    private fun getTaskLines(progress: Float): List<Component> {
        val activeTasks = taskManager.getActiveTasks()
        val speed = formatTotalSpeed(activeTasks)

        // Prepare text to display
        val textLines = mutableListOf<Component>()
        textLines.add("Total: ${percentFormat.format(progress * 100)}%$speed".asPlainText())
        textLines.add(PlainText.EMPTY)

        activeTasks.take(3).forEach { task ->
            textLines.add(Pools.buildStringPooled {
                append(task.name)
                append(": ")
                append(percentFormat.format(task.progress * 100))
                append('%')
                append(formatTotalSpeed(listOf(task)))
            }.asPlainText(ChatFormatting.GRAY))
        }

        if (activeTasks.size > 3) {
            textLines.add("... and ${activeTasks.size - 3} more tasks".asPlainText(ChatFormatting.GRAY))
        }
        return textLines
    }

    private fun formatTotalSpeed(tasks: List<Task>): String {
        val total = calculateTotalSpeed(tasks)

        return if (total > 0) {
            " (${total.formatAsCapacity()}/s)"
        } else {
            ""
        }
    }

    private fun calculateTotalSpeed(tasks: List<Task>): Long {
        return tasks.filter { task ->
            !task.isCompleted
        }.sumOf { task ->
            ((task as? ResourceTask)?.speed ?: 0L) + calculateTotalSpeed(task.subTasks.values.toList())
        }
    }

    override fun tick() {
        if (taskManager.isCompleted && (BrowserBackendManager.backend?.isInitialized == true || isBrowserDisabled)) {
            mc.gui.setScreen(TitleScreen())
        }
    }

    override fun shouldCloseOnEsc() = false

    override fun isPauseScreen() = false

}
