import type {ConfigurableSetting, ModuleSetting} from "../../integration/types";

export function configurable(
    name: string,
    value: ModuleSetting[],
    description?: string,
): ConfigurableSetting {
    return {
        name,
        valueType: "CONFIGURABLE",
        value,
        description,
        key: `preview.${name}`,
    };
}

export function togglable(name: string, value: ModuleSetting[]): ModuleSetting {
    return {
        name,
        valueType: "TOGGLEABLE",
        value,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function booleanSetting(
    name: string,
    value: boolean,
    description?: string,
): ModuleSetting {
    return {
        name,
        valueType: "BOOLEAN",
        value,
        description,
        key: `preview.${name}`,
    };
}

export function floatSetting(
    name: string,
    value: number,
    from: number,
    to: number,
    suffix: string,
): ModuleSetting {
    return {
        name,
        valueType: "FLOAT",
        value,
        range: {from, to},
        suffix,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function intSetting(
    name: string,
    value: number,
    from: number,
    to: number,
    suffix: string,
): ModuleSetting {
    return {
        name,
        valueType: "INT",
        value,
        range: {from, to},
        suffix,
        description: undefined,
        key: `preview.${name}`,
    };
}

export function intRangeSetting(
    name: string,
    fromValue: number,
    toValue: number,
    from: number,
    to: number,
): ModuleSetting {
    return {
        name,
        valueType: "INT_RANGE",
        value: {from: fromValue, to: toValue},
        range: {from, to},
        suffix: "",
        description: undefined,
        key: `preview.${name}`,
    };
}

export function chooseSetting(
    name: string,
    value: string,
    choices: string[],
): ModuleSetting {
    return {
        name,
        valueType: "CHOOSE",
        value,
        choices,
        description: undefined,
        key: `preview.${name}`,
    };
}
