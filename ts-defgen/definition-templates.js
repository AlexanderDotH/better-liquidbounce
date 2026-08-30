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

function className(javaClass) {
    const fullName = javaClass.name;
    return fullName.substring(fullName.lastIndexOf(".") + 1);
}

function typeImports(javaClasses) {
    return javaClasses.map(javaClass => {
        const name = className(javaClass);
        return `import { ${name} as ${name}_ } from "../types/${javaClass.name.replaceAll(".", "/")}";`;
    }).join("\n");
}

function globalValueExports(globalEntries, isJavaClass) {
    return globalEntries
        .filter(entry => entry[1] !== undefined)
        .filter(entry => !isJavaClass(entry[1]))
        .filter(entry => entry[1].class !== undefined)
        .map(entry => `    export const ${entry[0]}: ${className(entry[1].class)}_;`)
        .join("\n\n");
}

function globalClassExports(javaClasses, globalEntries, isJavaClass) {
    return javaClasses.map(javaClass => {
        const exported = globalEntries.find(([, value]) => isJavaClass(value) && value === javaClass);
        if (!exported) {
            return null;
        }
        const type = javaClass.isInterface?.() ? className(javaClass) + "_" : "typeof " + className(javaClass) + "_";
        return `    export const ${exported[0]}: ${type};`;
    }).filter(entry => entry !== null).join("\n\n");
}

function buildEmbeddedDefinition(javaClasses, globalEntries, isJavaClass) {
    return `
// ambient.ts
// imports
import "../augmentations/index.d.ts"
${typeImports(javaClasses)}
declare global {


// exports
${globalValueExports(globalEntries, isJavaClass)}

${globalClassExports(javaClasses, globalEntries, isJavaClass)}

}
`;
}

function eventImports(eventEntries) {
    return eventEntries
        .map(entry => entry[1])
        .map(eventClass => `import type { ${eventClass.simpleName} } from '../types/${eventClass.qualifiedName.replaceAll(".", "/")}.d.ts'`)
        .join("\n");
}

function eventOverloads(eventEntries) {
    return eventEntries
        .map(entry => `on(eventName: "${entry[0]}", handler: (${entry[0]}Event: ${entry[1].simpleName}) => void): Unit;`)
        .join("\n");
}

function buildScriptModuleAugmentation(eventEntries) {
    return `// ScriptModule augmentation - adds event handler interfaces

// Event type imports

// imports for
${eventImports(eventEntries)}


import type { Unit } from '../types/kotlin/Unit';

// Augment ScriptModule with specific event handler overloads
declare module '../types/net/ccbluex/liquidbounce/script/bindings/features/ScriptModule' {
    interface ScriptModule {
        on(eventName: "enable" | "disable", handler: () => void): Unit;

        // on events with specific event types

// on events
${eventOverloads(eventEntries)}


    }
}
`;
}

module.exports = {
    buildEmbeddedDefinition,
    buildScriptModuleAugmentation,
    eventImports,
    eventOverloads,
};
