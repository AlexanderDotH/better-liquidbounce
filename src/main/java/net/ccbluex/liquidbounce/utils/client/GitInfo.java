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
package net.ccbluex.liquidbounce.utils.client;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

@NullMarked
public final class GitInfo {

  private GitInfo() {}

  public static String version() {
    return net.ccbluex.liquidbounce.common.GitInfo.version();
  }

  public static String branch() {
    return net.ccbluex.liquidbounce.common.GitInfo.branch();
  }

  public static @Nullable String get(String key) {
    return net.ccbluex.liquidbounce.common.GitInfo.get(key);
  }

  public static String getOrDefault(String key, String defaultValue) {
    return net.ccbluex.liquidbounce.common.GitInfo.getOrDefault(key, defaultValue);
  }

  public static Set<Map.Entry<Object, Object>> entries() {
    return net.ccbluex.liquidbounce.common.GitInfo.entries();
  }

}
