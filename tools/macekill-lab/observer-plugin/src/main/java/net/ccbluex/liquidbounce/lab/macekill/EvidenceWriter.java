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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

final class EvidenceWriter implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(ZoneOffset.UTC);
    private final BufferedWriter writer;

    private EvidenceWriter(BufferedWriter writer) {
        this.writer = writer;
    }

    static EvidenceWriter open(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("macekill-lab_" + FILE_TIME.format(Instant.now()) + ".jsonl");
        return new EvidenceWriter(Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        ));
    }

    synchronized void write(String event, long tick, Map<String, ?> fields) throws IOException {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("schemaVersion", 1);
        entry.put("time", Instant.now().toString());
        entry.put("tick", tick);
        entry.put("event", event);
        entry.putAll(fields);
        writer.write(JsonEncoder.encode(entry));
        writer.newLine();
        writer.flush();
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException ignored) {
            // The first evidence failure was already reported by the plugin.
        }
    }
}
