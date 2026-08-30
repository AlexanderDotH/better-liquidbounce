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

package net.ccbluex.liquidbounce.interfaces;

import org.jspecify.annotations.Nullable;

/**
 * Neutral input boundary implemented by the active chat screen mixin.
 */
public interface ChatScreenInput {

    /**
     * Replaces the current chat input when it is available.
     *
     * @return {@code true} when the existing input accepted the text
     */
    boolean setInitial(String text);

    /**
     * Tries to reuse an already open chat screen without depending on its mixin implementation.
     */
    static boolean startTyping(@Nullable Object screen, String text) {
        if (!(screen instanceof ChatScreenInput input)) {
            return false;
        }

        return input.setInitial(text);
    }
}
