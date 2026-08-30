import type {BaritoneSetting, BaritoneSettingValue} from "./types";

export function filterBaritoneSettings(
    settings: readonly BaritoneSetting[],
    query: string,
): BaritoneSetting[] {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) {
        return [...settings];
    }

    return settings.filter(setting => {
        const searchText = `${setting.name} ${setting.description}`.toLocaleLowerCase();
        return searchText.includes(normalizedQuery);
    });
}

export function coerceBaritoneSettingValue(
    setting: BaritoneSetting,
    rawValue: string | boolean,
): BaritoneSettingValue {
    if (setting.type === "BOOLEAN") {
        return coerceBoolean(rawValue);
    }

    if (setting.type === "INTEGER" || setting.type === "LONG") {
        return coerceInteger(rawValue);
    }

    if (setting.type === "FLOAT" || setting.type === "DOUBLE") {
        return coerceNumber(rawValue);
    }

    if (setting.type === "STRING_LIST") {
        return String(rawValue)
            .split(/[,\n]/)
            .map(value => value.trim())
            .filter(Boolean);
    }

    const value = String(rawValue);
    if (setting.type === "ENUM" && !setting.options?.includes(value)) {
        throw new Error(`${setting.name} must be one of its supported values.`);
    }

    return value;
}

function coerceBoolean(rawValue: string | boolean): boolean {
    if (typeof rawValue === "boolean") {
        return rawValue;
    }

    const normalizedValue = rawValue.trim().toLocaleLowerCase();
    if (normalizedValue === "true") {
        return true;
    }
    if (normalizedValue === "false") {
        return false;
    }
    throw new Error("Boolean settings must be true or false.");
}

function coerceInteger(rawValue: string | boolean): number {
    const value = Number(rawValue);
    if (Number.isFinite(value) && Number.isInteger(value)) {
        return value;
    }
    throw new Error("This setting requires a whole number.");
}

function coerceNumber(rawValue: string | boolean): number {
    const value = Number(rawValue);
    if (Number.isFinite(value)) {
        return value;
    }
    throw new Error("This setting requires a finite number.");
}
