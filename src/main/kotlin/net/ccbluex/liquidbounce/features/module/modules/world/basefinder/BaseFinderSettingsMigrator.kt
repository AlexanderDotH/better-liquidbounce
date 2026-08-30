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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal class BaseFinderSettingsMigrator(private val root: JsonArray) {
    private val byName = root.filter { it.isJsonObject }.map { it.asJsonObject }
        .associateBy { it["name"]?.asString.orEmpty() }
        .toMutableMap()

    fun renameRootGroup(from: String, to: String) {
        if (to in byName || from !in byName) return
        val group = byName.remove(from) ?: return
        group.addProperty("name", to)
        byName[to] = group
    }

    fun foldLegacySeedMismatchToggle() {
        val legacyToggle = takeNestedBoolean("Evidence", "SeedMismatch") ?: takeRootBoolean("SeedMismatch")
        val legacyEnabled = legacyToggle
            ?.get("value")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean
            ?: return
        val nested = groupChildren("SeedMismatch")
        if ("Enabled" in nestedNames(nested)) return
        nested.add(JsonObject().apply {
            addProperty("name", "Enabled")
            addProperty("value", legacyEnabled)
        })
    }

    fun moveGroupInto(parentGroupName: String, groupName: String) {
        val moving = takeRoot(groupName) ?: return
        val parent = groupChildren(parentGroupName)
        val existing = parent.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == groupName }
        if (existing == null) {
            parent.add(moving)
            return
        }
        mergeGroupChildren(parent, existing, moving)
    }

    fun moveInto(groupName: String, names: Collection<String>) {
        val nested = groupChildren(groupName)
        val present = nestedNames(nested)
        names.forEach { name ->
            if (name in present) takeRoot(name) else takeRoot(name)?.let(nested::add)
        }
    }

    fun dropRoot(names: Collection<String>) {
        names.forEach { takeRoot(it) }
    }

    fun dropNested(groupName: String, names: Collection<String>) {
        names.forEach { takeNested(groupName, it) }
    }

    fun dropFromNestedGroup(parentGroupName: String, groupName: String, names: Collection<String>) {
        val nested = nestedGroupChildren(parentGroupName, groupName) ?: return
        names.forEach { name -> removeNamedChild(nested, name) }
    }

    fun bumpLegacyScanChunksCountToRadius() {
        val nested = nestedGroupChildren("Evidence", "SeedMismatch") ?: return
        val entry = namedChild(nested, "ScanChunks") ?: return
        val value = entry["value"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: return
        if (value == LEGACY_SCAN_CHUNKS_SPIRAL_COUNT) entry.addProperty("value", DEFAULT_SCAN_CHUNKS_RADIUS)
    }

    private fun mergeGroupChildren(parent: JsonArray, existing: JsonObject, moving: JsonObject) {
        val existingChildren = existing["value"]?.takeIf { it.isJsonArray }?.asJsonArray
        val movingChildren = moving["value"]?.takeIf { it.isJsonArray }?.asJsonArray
        if (existingChildren == null || movingChildren == null) {
            parent.remove(existing)
            parent.add(moving)
            return
        }
        val present = nestedNames(existingChildren).toMutableSet()
        movingChildren.filter { it.isJsonObject }.forEach { child ->
            val name = child.asJsonObject["name"]?.asString
            if (name == null || present.add(name)) existingChildren.add(child)
        }
    }

    private fun takeRoot(name: String): JsonObject? {
        val entry = byName.remove(name) ?: return null
        root.remove(entry)
        return entry
    }

    private fun takeRootBoolean(name: String): JsonObject? {
        val entry = byName[name] ?: return null
        if (!entry.hasBooleanValue()) return null
        return takeRoot(name)
    }

    private fun takeNestedBoolean(groupName: String, childName: String): JsonObject? {
        val nested = byName[groupName]?.jsonArrayValue() ?: return null
        val child = namedChild(nested, childName) ?: return null
        if (!child.hasBooleanValue()) return null
        nested.remove(child)
        return child
    }

    private fun takeNested(groupName: String, childName: String): JsonObject? {
        val nested = byName[groupName]?.jsonArrayValue() ?: return null
        val child = namedChild(nested, childName) ?: return null
        nested.remove(child)
        return child
    }

    private fun groupChildren(groupName: String): JsonArray {
        val existing = byName[groupName]
        if (existing != null) return existing.ensureJsonArrayValue()
        val nested = JsonArray()
        val group = JsonObject().apply {
            addProperty("name", groupName)
            add("value", nested)
        }
        root.add(group)
        byName[groupName] = group
        return nested
    }

    private fun nestedGroupChildren(parentGroupName: String, groupName: String): JsonArray? {
        val parentChildren = byName[parentGroupName]?.jsonArrayValue() ?: return null
        return namedChild(parentChildren, groupName)?.jsonArrayValue()
    }

    private fun JsonObject.ensureJsonArrayValue(): JsonArray {
        val current = this["value"]
        if (current != null && current.isJsonArray) return current.asJsonArray
        return JsonArray().also { add("value", it) }
    }

    private fun JsonObject.jsonArrayValue(): JsonArray? =
        this["value"]?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.hasBooleanValue(): Boolean =
        this["value"]?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean } == true

    private fun namedChild(group: JsonArray, name: String): JsonObject? =
        group.filter { it.isJsonObject }.map { it.asJsonObject }
            .firstOrNull { it["name"]?.asString == name }

    private fun removeNamedChild(group: JsonArray, name: String) {
        namedChild(group, name)?.let(group::remove)
    }

    private fun nestedNames(group: JsonArray): Set<String> =
        group.filter { it.isJsonObject }.mapNotNull { it.asJsonObject["name"]?.asString }.toSet()
}

private const val LEGACY_SCAN_CHUNKS_SPIRAL_COUNT = 9
private const val DEFAULT_SCAN_CHUNKS_RADIUS = 12
