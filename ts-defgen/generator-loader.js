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

const { URLClassLoader } = require("@ccbluex/liquidbounce-script-api/java/net/URLClassLoader");
const { File } = require("@ccbluex/liquidbounce-script-api/java/io/File");
const { Thread } = require("@ccbluex/liquidbounce-script-api/java/lang/Thread");

function createClassLoaderFromJar(jarPath) {
    try {
        const jarUrl = new File(jarPath).toURI().toURL();
        return new URLClassLoader([jarUrl], Thread.currentThread().getContextClassLoader());
    } catch (error) {
        console.error("Error creating ClassLoader:", error);
        throw error;
    }
}

function loadClassFromJar(classLoader, className) {
    try {
        return classLoader.loadClass(className);
    } catch (error) {
        console.error(`Error loading class ${className}:`, error);
        throw error;
    }
}

function loadGeneratorClasses(rootPath) {
    const loader = createClassLoaderFromJar(rootPath + "/ts-generator.jar");
    const NPMGen = loadClassFromJar(loader, "me.commandblock2.tsGenerator.NPMPackageGenerator");
    const TsGen = loadClassFromJar(loader, "me.ntrrgc.tsGenerator.TypeScriptGenerator");
    const VoidType = loadClassFromJar(loader, "me.ntrrgc.tsGenerator.VoidType");
    return { NPMGen, TsGen, NULL: VoidType.getEnumConstants()[0] };
}

module.exports = { loadGeneratorClasses };
