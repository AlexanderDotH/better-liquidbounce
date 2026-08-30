import type {ConfigurableSetting, ModuleSetting} from "../../integration/types";
import {configurable, floatSetting, intSetting} from "./previewSettingFactories.ts";

export function choiceSetting(name: string): ModuleSetting {
    const adaptive = configurable("Adaptive", [
        floatSetting("BurstDelay", 90, 0, 500, "ms"),
    ]);
    const steady = configurable("Steady", [
        intSetting("ClicksPerSecond", 11, 1, 20, " cps"),
    ]);

    return {
        name,
        valueType: "CHOICE",
        value: [],
        active: "Adaptive",
        choices: {Adaptive: adaptive, Steady: steady},
        categories: {Dynamic: ["Adaptive"], Simple: ["Steady"]},
        description: undefined,
        key: `preview.${name}`,
    };
}

export function choiceModes(
    name: string,
    active: string,
    choices: Record<string, ConfigurableSetting>,
): ModuleSetting {
    return {
        name,
        valueType: "CHOICE",
        value: [],
        active,
        choices,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function multiChooseSetting(
    name: string,
    value: string[],
    choices: string[],
): ModuleSetting {
    return {
        name,
        valueType: "MULTI_CHOOSE",
        value,
        choices,
        canBeNone: false,
        isOrderSensitive: false,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function textSetting(name: string, value: string): ModuleSetting {
    return {
        name,
        valueType: "TEXT",
        value,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function bindSetting(name: string, boundKey: string): ModuleSetting {
    return {
        name,
        valueType: "BIND",
        value: {
            boundKey,
            action: "Toggle",
            modifiers: [],
        },
        defaultValue: {
            boundKey: "key.keyboard.unknown",
            action: "Toggle",
            modifiers: [],
        },
        description: undefined,
        key: `preview.${name}`,
    };
}

export function mutableListSetting(name: string, value: string[]): ModuleSetting {
    return {
        name,
        valueType: "MUTABLE_LIST",
        value,
        innerValueType: "TEXT",
        description: undefined,
        key: `preview.${name}`,
    };
}

export function registryListSetting(
    name: string,
    value: string[],
    registry: string,
): ModuleSetting {
    return {
        name,
        valueType: "REGISTRY_LIST",
        value,
        innerValueType: "TEXT",
        registry,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function merchantTradeFiltersSetting(): ModuleSetting {
    return {
        name: "Trades",
        valueType: "MERCHANT_TRADE_FILTERS",
        value: [
            {
                inputA: ["minecraft:emerald"],
                inputB: [],
                outputs: ["minecraft:bread"],
            },
            {
                inputA: ["minecraft:paper"],
                inputB: ["minecraft:book"],
                outputs: ["minecraft:enchanted_book"],
            },
        ],
        registry: "item",
        description: "Ordered item-only rules processed round-robin.",
        key: "preview.AutoShop.Trades",
    };
}

export function merchantReachSetting(): ModuleSetting {
    return {
        name: "Reach",
        valueType: "MERCHANT_REACH",
        value: {range: 4.5, wallRange: 3},
        rangeBounds: {from: 1, to: 6},
        wallRangeBounds: {from: 0, to: 6},
        suffix: "blocks",
        description: "Uses the shorter wall range for merchants without line of sight.",
        key: "preview.AutoShop.Reach",
    };
}
