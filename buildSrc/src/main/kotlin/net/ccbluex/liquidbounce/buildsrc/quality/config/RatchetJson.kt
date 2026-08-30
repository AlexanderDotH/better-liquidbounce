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

package net.ccbluex.liquidbounce.buildsrc.quality.config

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetBaseline
import net.ccbluex.liquidbounce.buildsrc.quality.ratchet.RatchetEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

object RatchetJson {
    fun read(path: Path): RatchetBaseline = parse(Files.readString(path), path.toString())

    fun readOrNull(path: Path): RatchetBaseline? = path.takeIf(Path::exists)?.let(::read)

    fun parse(content: String, description: String = "ratchet JSON"): RatchetBaseline {
        val parsed = JsonSlurper().parseText(content)
        require(parsed is Map<*, *>) { "$description must contain a JSON object" }
        return RatchetBaseline(
            schemaVersion = parsed.intValue("schemaVersion"),
            capturedRevision = parsed.stringValue("capturedRevision"),
            entries = parsed.listValue("entries").map(::entry).sortedBy(RatchetEntry::fingerprint),
        )
    }

    fun write(path: Path, baseline: RatchetBaseline) {
        val document = linkedMapOf(
            "schemaVersion" to baseline.schemaVersion,
            "capturedRevision" to baseline.capturedRevision,
            "entries" to baseline.entries.sortedBy(RatchetEntry::fingerprint).map(::entryMap),
        )
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, JsonOutput.prettyPrint(JsonOutput.toJson(document)) + "\n")
    }

    private fun entry(value: Any?): RatchetEntry {
        require(value is Map<*, *>) { "Ratchet entries must be objects" }
        return RatchetEntry(
            fingerprint = value.stringValue("fingerprint"),
            ruleId = value.stringValue("ruleId"),
            path = value.stringValue("path"),
            subject = value.stringValue("subject"),
            maximum = value.intValue("maximum"),
            targets = value.optionalStringSet("targets"),
        )
    }

    private fun entryMap(entry: RatchetEntry) = linkedMapOf(
        "fingerprint" to entry.fingerprint,
        "ruleId" to entry.ruleId,
        "path" to entry.path,
        "subject" to entry.subject,
        "maximum" to entry.maximum,
        "targets" to entry.targets.sorted(),
    )
}

private fun Map<*, *>.stringValue(name: String) = requireNotNull(this[name] as? String) { "Missing string '$name'" }
private fun Map<*, *>.intValue(name: String) = requireNotNull((this[name] as? Number)?.toInt()) { "Missing integer '$name'" }
private fun Map<*, *>.listValue(name: String) = requireNotNull(this[name] as? List<*>) { "Missing array '$name'" }
private fun Map<*, *>.optionalStringSet(name: String): Set<String> {
    val values = this[name] ?: return emptySet()
    require(values is List<*>) { "'$name' must be an array" }
    return values.mapTo(sortedSetOf()) { value ->
        requireNotNull(value as? String) { "'$name' must contain only strings" }
    }
}
