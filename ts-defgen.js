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

"use strict";
Object.defineProperty(exports, "__esModule", { value: true });

const generatorLoader = require("./ts-defgen/generator-loader");
const classDiscovery = require("./ts-defgen/class-discovery");
const definitionOutput = require("./ts-defgen/definition-output");
const { ScriptManager } = require("@ccbluex/liquidbounce-script-api/net/ccbluex/liquidbounce/script/ScriptManager");
const { LiquidBounce } = require("@ccbluex/liquidbounce-script-api/net/ccbluex/liquidbounce/LiquidBounce");

const inDevelopment = LiquidBounce.IN_DEVELOPMENT;
const script = registerScript.apply({
    name: "ts-defgen",
    version: "1.0.0",
    authors: ["commandblock2", "CCBlueX"],
});

function generate(rootPath, packageName) {
    try {
        const generators = generatorLoader.loadGeneratorClasses(rootPath);
        const discovery = classDiscovery.discoverClasses();
        const generated = classDiscovery.generateTypes(
            generators.TsGen,
            generators.NULL,
            discovery.kotlinClasses,
        );
        definitionOutput.writePackage(
            generators.NPMGen,
            generated,
            packageName,
            rootPath,
            inDevelopment,
        );
        definitionOutput.writeDefinitions(
            rootPath,
            packageName,
            discovery,
            value => value instanceof classDiscovery.Class,
        );
    } catch (error) {
        console.error(error);
        Client.displayChatMessage(`Error generating TypeScript definitions: ${error.message}`);
        throw error;
    }
}

const packageName = "@ccbluex/liquidbounce-script-api";
const rootPath = ScriptManager.INSTANCE.root.path;
if (Java.type("java.lang.System").getenv("SCRIPT_TYPEGEN_BUILD")) {
    generate(rootPath, packageName);
    mc.close();
}

script.registerCommand({
    name: "ts-defgen",
    aliases: ["tsgen"],
    parameters: [],
    onExecute() {
        UnsafeThread.run(() => generate(rootPath, packageName));
    },
});
