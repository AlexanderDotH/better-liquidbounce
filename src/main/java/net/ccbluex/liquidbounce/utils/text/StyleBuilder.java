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

package net.ccbluex.liquidbounce.utils.text;

import net.minecraft.network.chat.*;
import org.jspecify.annotations.Nullable;

public final class StyleBuilder {

    private final Style base;

    public StyleBuilder(Style base) {
        this.base = base;
    }

    public StyleBuilder() {
        this(Style.EMPTY);
    }

    @Nullable
    public TextColor color;
    @Nullable
    public Integer shadowColor;
    @Nullable
    public Boolean bold;
    @Nullable
    public Boolean italic;
    @Nullable
    public Boolean underlined;
    @Nullable
    public Boolean strikethrough;
    @Nullable
    public Boolean obfuscated;
    @Nullable
    public ClickEvent clickEvent;
    @Nullable
    public HoverEvent hoverEvent;
    @Nullable
    public String insertion;
    @Nullable
    public FontDescription font;

    public Style build() {
        Style style = this.base;
        if (this.color != null) style = style.withColor(this.color);
        if (this.shadowColor != null) style = style.withShadowColor(this.shadowColor);
        if (this.bold != null) style = style.withBold(this.bold);
        if (this.italic != null) style = style.withItalic(this.italic);
        if (this.underlined != null) style = style.withUnderlined(this.underlined);
        if (this.strikethrough != null) style = style.withStrikethrough(this.strikethrough);
        if (this.obfuscated != null) style = style.withObfuscated(this.obfuscated);
        if (this.clickEvent != null) style = style.withClickEvent(this.clickEvent);
        if (this.hoverEvent != null) style = style.withHoverEvent(this.hoverEvent);
        if (this.insertion != null) style = style.withInsertion(this.insertion);
        if (this.font != null) style = style.withFont(this.font);
        return style.equals(Style.EMPTY) ? Style.EMPTY : style;
    }

}
