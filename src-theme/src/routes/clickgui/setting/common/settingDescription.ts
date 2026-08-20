import type {ModuleSetting} from "../../../../integration/types";

const DESCRIPTION_OWNING_SETTING_TYPES = new Set([
    "CHOICE",
    "CONFIGURABLE",
    "TOGGLEABLE",
]);

function nonBlank(text: string | undefined): string | undefined {
    return text?.trim() ? text : undefined;
}

export function preferredSettingDescription(setting: ModuleSetting): string | undefined {
    return nonBlank(setting.extendedDescription) ?? nonBlank(setting.description);
}

/** Nested setting families attach the action to their own header; modes retain option-specific dropdown text. */
export function settingShiftDescription(setting: ModuleSetting): string | undefined {
    if (DESCRIPTION_OWNING_SETTING_TYPES.has(setting.valueType)) {
        return undefined;
    }

    return preferredSettingDescription(setting);
}
