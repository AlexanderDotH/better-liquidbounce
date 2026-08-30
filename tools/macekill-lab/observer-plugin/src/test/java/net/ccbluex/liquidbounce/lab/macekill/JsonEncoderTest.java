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

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JsonEncoderTest {
    @Test
    void preservesTheEvidenceFieldOrderAndEscapingContract() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("event", "move");
        evidence.put("cancelled", false);
        evidence.put("message", "line\n\"quoted\"");

        String encoded = JsonEncoder.encode(evidence);

        assertEquals(
            "{\"schemaVersion\":1,\"event\":\"move\",\"cancelled\":false,\"message\":\"line\\n\\\"quoted\\\"\"}",
            encoded
        );
    }
}
