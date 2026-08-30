/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.utils.text

import net.ccbluex.liquidbounce.interfaces.TextColorAddition
import net.ccbluex.liquidbounce.lang.translation
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.io.File

fun regular(text: MutableComponent): MutableComponent = text.withStyle(ChatFormatting.GRAY)
fun regular(text: String): MutableComponent = text.asText().withStyle(ChatFormatting.GRAY)
fun variable(text: MutableComponent): MutableComponent = text.withStyle(ChatFormatting.GOLD)
fun variable(text: String): MutableComponent = text.asText().withStyle(ChatFormatting.GOLD)

fun clickablePath(file: File): MutableComponent = variable(file.absolutePath)
    .onClick(ClickEvent.OpenFile(file))
    .onHover(HoverEvent.ShowText("Open".asPlainText()))

fun highlight(text: MutableComponent): MutableComponent = text
    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x0080FF)).withBold(true))
fun highlight(text: String): MutableComponent = highlight(text.asText())
fun warning(text: MutableComponent): MutableComponent = text.withStyle(ChatFormatting.YELLOW)
fun warning(text: String): MutableComponent = text.asText().withStyle(ChatFormatting.YELLOW)
fun markAsError(text: String): MutableComponent = text.asText().withStyle(ChatFormatting.RED)
fun markAsError(text: MutableComponent): MutableComponent = text.withStyle(ChatFormatting.RED)

inline fun MutableComponent.withColor(value: ChatFormatting?): MutableComponent = setStyle(style.withColor(value))
inline fun MutableComponent.withColor(value: TextColor?): MutableComponent = setStyle(style.withColor(value))
inline fun MutableComponent.bold(value: Boolean?): MutableComponent = setStyle(style.withBold(value))
inline fun MutableComponent.obfuscated(value: Boolean?): MutableComponent = setStyle(style.withObfuscated(value))
inline fun MutableComponent.strikethrough(value: Boolean?): MutableComponent = setStyle(style.withStrikethrough(value))
inline fun MutableComponent.underline(value: Boolean?): MutableComponent = setStyle(style.withUnderlined(value))
inline fun MutableComponent.italic(value: Boolean?): MutableComponent = setStyle(style.withItalic(value))
inline fun MutableComponent.onHover(event: HoverEvent?): MutableComponent = setStyle(style.withHoverEvent(event))
inline fun MutableComponent.onClick(event: ClickEvent?): MutableComponent = setStyle(style.withClickEvent(event))
inline fun MutableComponent.onClickRun(callback: Runnable): MutableComponent =
    setStyle(style.withClickEvent(RunnableClickEvent(callback)))
inline operator fun MutableComponent.plusAssign(other: String) { append(other) }
inline operator fun MutableComponent.plusAssign(other: Component) { append(other) }

fun MutableComponent.copyable(
    copyContent: String = string,
    hover: HoverEvent? = HoverEvent.ShowText(translation("liquidbounce.tooltip.clickToCopy")),
): MutableComponent = apply {
    hover?.let(::onHover)
    onClick(ClickEvent.CopyToClipboard(copyContent))
}

fun MutableComponent.bypassNameProtection(): MutableComponent = withStyle {
    val color = it.color ?: TextColor.fromLegacyFormat(ChatFormatting.RESET)
    @Suppress("CAST_NEVER_SUCCEEDS")
    val newColor = (color as TextColorAddition).`liquid_bounce$withNameProtectionBypass`()
    it.withColor(newColor)
}

val TextColor.bypassesNameProtection: Boolean
    @Suppress("CAST_NEVER_SUCCEEDS")
    get() = (this as TextColorAddition).`liquid_bounce$doesBypassingNameProtect`()
