import assert from "node:assert/strict";
import test from "node:test";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const templates = require("../../ts-defgen/definition-templates.js");

test("embedded definitions preserve constructor, interface, and global binding shapes", () => {
    const stringClass = { name: "java.lang.String", isInterface: () => false };
    const contractClass = { name: "example.Contract", isInterface: () => true };
    const clientClass = { name: "example.Client" };
    const javaClasses = [stringClass, contractClass];
    const globalEntries = [
        ["String", stringClass],
        ["Contract", contractClass],
        ["Client", { class: clientClass }],
    ];
    const isJavaClass = value => value === stringClass || value === contractClass;

    const definition = templates.buildEmbeddedDefinition(javaClasses, globalEntries, isJavaClass);

    assert.match(definition, /import \{ String as String_ \} from "\.\.\/types\/java\/lang\/String";/);
    assert.match(definition, /export const Client: Client_;/);
    assert.match(definition, /export const String: typeof String_;/);
    assert.match(definition, /export const Contract: Contract_;/);
});

test("ScriptModule augmentation preserves typed event overloads", () => {
    const eventEntries = [["tick", {
        simpleName: "GameTickEvent",
        qualifiedName: "net.ccbluex.liquidbounce.event.GameTickEvent",
    }]];

    const augmentation = templates.buildScriptModuleAugmentation(eventEntries);

    assert.match(augmentation, /import type \{ GameTickEvent \} from '\.\.\/types\/net\/ccbluex\/liquidbounce\/event\/GameTickEvent\.d\.ts'/);
    assert.match(augmentation, /on\(eventName: "tick", handler: \(tickEvent: GameTickEvent\) => void\): Unit;/);
    assert.match(augmentation, /on\(eventName: "enable" \| "disable", handler: \(\) => void\): Unit;/);
});
