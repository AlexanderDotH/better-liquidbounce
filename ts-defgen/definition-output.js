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

const { Paths } = require("@ccbluex/liquidbounce-script-api/java/nio/file/Paths");
const { LiquidBounce } = require("@ccbluex/liquidbounce-script-api/net/ccbluex/liquidbounce/LiquidBounce");
const { LocalDate } = require("@ccbluex/liquidbounce-script-api/java/time/LocalDate");
const { DateTimeFormatter } = require("@ccbluex/liquidbounce-script-api/java/time/format/DateTimeFormatter");
const templates = require("./definition-templates");

function packageVersion(inDevelopment) {
    if (inDevelopment) {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("y.M.d"));
    }
    const client = LiquidBounce.INSTANCE;
    return `${client.clientVersion}+${client.clientBranch}.${client.clientCommit}`;
}

function writePackage(NPMGen, generated, packageName, rootPath, inDevelopment) {
    Client.displayChatMessage("writing types");
    const npmPack = new NPMGen(
        generated,
        packageName,
        packageVersion(inDevelopment),
        `"augmentations/**/*.d.ts", "ambient/ambient.d.ts"`,
        `"./augmentations/*", "ambient/ambient.d.ts"`,
        `"types": "ambient/ambient.d.ts"`,
        null,
    );
    npmPack.writePackageTo(Paths.get(rootPath));
}

function writeString(path, content) {
    const Files = Java.type("java.nio.file.Files");
    const filePath = Paths.get(path);
    Files.createDirectories(filePath.getParent());
    Files.writeString(filePath, content, Java.type("java.nio.charset.StandardCharsets").UTF_8);
}

function writeDefinitions(rootPath, packageName, discovery, isJavaClass) {
    Client.displayChatMessage("print embedded script types, see log for more info, those are for maintainace use");
    const embedded = templates.buildEmbeddedDefinition(
        discovery.javaClasses,
        discovery.globalEntries,
        isJavaClass,
    );
    const augmentation = templates.buildScriptModuleAugmentation(discovery.eventEntries);
    Client.displayChatMessage("Generated TypeScript definitions successfully!");
    Client.displayChatMessage(`Output path: ${rootPath}`);
    console.log(embedded);
    writeString(`${rootPath}/${packageName}/ambient/ambient.d.ts`, embedded);
    writeString(`${rootPath}/${packageName}/augmentations/ScriptModule.augmentation.d.ts`, augmentation);
    console.log(templates.eventImports(discovery.eventEntries));
    console.log(templates.eventOverloads(discovery.eventEntries));
}

module.exports = { writeDefinitions, writePackage };
