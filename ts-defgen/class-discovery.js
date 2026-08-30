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

"use strict";

const { Thread } = require("@ccbluex/liquidbounce-script-api/java/lang/Thread");
const { HashMap } = require("@ccbluex/liquidbounce-script-api/java/util/HashMap");
const { ArrayList } = require("@ccbluex/liquidbounce-script-api/java/util/ArrayList");
const { JvmClassMappingKt } = require("@ccbluex/liquidbounce-script-api/kotlin/jvm/JvmClassMappingKt");
const { Class } = require("@ccbluex/liquidbounce-script-api/java/lang/Class");
const { EventKt } = require("@ccbluex/liquidbounce-script-api/net/ccbluex/liquidbounce/event/EventKt");
const { ClassPath } = require("@ccbluex/liquidbounce-script-api/com/google/common/reflect/ClassPath");

function j2kSafe(javaClass) {
    try {
        return JvmClassMappingKt.getKotlinClass(javaClass);
    } catch (_) {
        return undefined;
    }
}

function findAllClassInfos() {
    return Java.from(ClassPath.from(Thread.currentThread().getContextClassLoader())
        .getTopLevelClasses().asList());
}

function discoverJavaClasses(globalEntries) {
    return globalEntries
        .filter(entry => entry[1] !== undefined)
        .map(entry => entry[1] instanceof Class ? entry[1] : entry[1].class)
        .filter(entry => entry !== undefined);
}

function discoverEvents() {
    return ReflectionUtil.getDeclaredField(EventKt, "EVENT_NAME_TO_CLASS").entrySet().toArray()
        .map(entry => [entry[0], j2kSafe(entry[1])])
        .filter(entry => entry[1]);
}

function loadJvmClasses(classNames) {
    return classNames.map(className => {
        try {
            return ReflectionUtil.classByName(className);
        } catch (_) {
            return null;
        }
    }).filter(javaClass => javaClass).map(j2kSafe).filter(kotlinClass => kotlinClass);
}

function discoverJvmClasses() {
    const required = [
        "java.net.URLClassLoader", "java.nio.file.Paths", "java.util.HashMap", "java.util.ArrayList",
        "java.util.jar.JarInputStream", "java.util.Map", "com.google.common.reflect.ClassPath",
        "kotlin.jvm.JvmClassMappingKt",
    ];
    Client.displayChatMessage("looking for all jvm classes");
    const allClassInfos = findAllClassInfos();
    Client.displayChatMessage(`found ${allClassInfos.length} classes, converting to kotlin classes`);
    const discovered = allClassInfos.map(classInfo => {
        try {
            return classInfo.getName();
        } catch (_) {
            return null;
        }
    });
    return loadJvmClasses(required.concat(discovered));
}

function discoverClasses() {
    const globalEntries = Object.entries(globalThis);
    const javaClasses = discoverJavaClasses(globalEntries);
    const eventEntries = discoverEvents();
    Client.displayChatMessage(`found ${eventEntries.length} events`);
    const jvmClassesInKotlin = discoverJvmClasses();
    Client.displayChatMessage(`converted to ${jvmClassesInKotlin.length} kotlin classes`);
    const scriptModule = ReflectionUtil.classByName(
        "net.ccbluex.liquidbounce.script.bindings.features.ScriptModule",
    );
    const kotlinClasses = javaClasses.concat([scriptModule]).map(j2kSafe)
        .concat(eventEntries.map(entry => entry[1])).filter(entry => entry).concat(jvmClassesInKotlin);
    return { globalEntries, javaClasses, eventEntries, kotlinClasses };
}

function generateTypes(TsGen, NULL, kotlinClasses) {
    const classes = new ArrayList(kotlinClasses);
    Client.displayChatMessage(`generating types for ${classes.length} classes`);
    Client.displayChatMessage("this may take a while, please wait...");
    return new TsGen(classes, new HashMap(), new ArrayList(), new ArrayList(), "number", NULL);
}

module.exports = {
    Class,
    discoverClasses,
    generateTypes,
};
