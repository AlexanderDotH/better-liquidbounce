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
package net.ccbluex.liquidbounce.lab.macekill;

import java.util.Map;

final class JsonEncoder {
    private JsonEncoder() {
    }

    static String encode(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder result = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    result.append(',');
                }
                first = false;
                result.append(encode(String.valueOf(entry.getKey()))).append(':').append(encode(entry.getValue()));
            }
            return result.append('}').toString();
        }
        if (value instanceof Position position) {
            return encode(Map.of(
                "world", position.world(),
                "x", position.x(),
                "y", position.y(),
                "z", position.z(),
                "yaw", position.yaw(),
                "pitch", position.pitch()
            ));
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
