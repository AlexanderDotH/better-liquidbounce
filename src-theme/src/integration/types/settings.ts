import type {Vec2, Vec3} from "./gameplay";
import type {Range} from "./hud";

export type ModuleSetting =
    BlocksSetting
    | ActionSetting
    | BooleanSetting
    | FloatSetting
    | FloatRangeSetting
    | IntSetting
    | IntRangeSetting
    | MerchantTradeFiltersSetting
    | MerchantReachSetting
    | ChoiceSetting
    | ChooseSetting
    | MultiChooseSetting
    | ListSetting
    | RegistryListSetting
    | ItemListSetting
    | RegistryMutableListSetting
    | ConfigurableSetting
    | TogglableSetting
    | ColorSetting
    | TextSetting
    | PlayerSetting
    | BindSetting
    | Vec2Setting
    | Vec3Setting
    | KeySetting
    | FileSetting
    | CurveSetting;

export type File = string;

export type FileDialogMode = "OPEN_FILE" | "OPEN_FOLDER" | "SAVE_FILE";

export interface FileSelectDialog {
    mode: FileDialogMode;
    supportedExtensions: string[] | undefined;
}

export interface FileSelectResult {
    file: File | undefined;
}

export interface Setting<V> {
    valueType: string;
    name: string;
    value: V;
    description: string | undefined;
    extendedDescription?: string | undefined;
    key: string | undefined;
}

export interface FileSetting extends Setting<File> {
    dialogMode: FileDialogMode;
    supportedExtensions: string[] | undefined;
}

export interface CurveSetting extends Setting<Vec2[]> {
    xAxis: {
        label: string;
        range: Range;
    },
    yAxis: {
        label: string;
        range: Range;
    }
    tension: number;
}

export interface BlocksSetting extends Setting<string[]> {
}

export interface KeySetting extends Setting<string> {
}

export interface BindSetting extends Setting<InputBind> {
    defaultValue: InputBind;
}

export interface TextSetting extends Setting<string> {
}

export interface PlayerSetting extends Setting<string> {
    registry: string;
}

export interface Vec2Setting extends Setting<Vec2> {
}

export interface Vec3Setting extends Setting<Vec3> {
    useLocateButton: boolean;
}

export interface ColorSetting extends Setting<number> {
}

export interface BooleanSetting extends Setting<boolean> {
}

export interface ActionSetting extends Setting<boolean> {
}

export interface RangedSettingWarning {
    threshold: number;
    message: string;
}

export interface FloatSetting extends Setting<number> {
    range: Range;
    suffix: string;
    warning?: RangedSettingWarning;
}

export interface FloatRangeSetting extends Setting<Range> {
    range: Range;
    suffix: string;
}

export interface IntSetting extends Setting<number> {
    range: Range;
    suffix: string;
}

export interface IntRangeSetting extends Setting<Range> {
    range: Range;
    suffix: string;
}

export interface MerchantTradeFilter {
    inputA: string[];
    inputB: string[];
    outputs: string[];
}

export interface MerchantTradeFiltersSetting extends Setting<MerchantTradeFilter[]> {
    valueType: "MERCHANT_TRADE_FILTERS";
    registry: string;
}

export interface MerchantReach {
    range: number;
    wallRange: number;
}

export interface MerchantReachSetting extends Setting<MerchantReach> {
    valueType: "MERCHANT_REACH";
    rangeBounds: Range;
    wallRangeBounds: Range;
    suffix: string;
}

export interface ChoiceSetting extends Setting<ModuleSetting[]> {
    active: string;
    choices: { [name: string]: ModuleSetting }
    categories?: { [name: string]: string[] }
}

export interface ChooseSetting extends Setting<string> {
    choices: string[];
}

export interface MultiChooseSetting extends Setting<string[]> {
    choices: string[];
    canBeNone: boolean;
    isOrderSensitive: boolean;
}

export interface ListSetting extends Setting<string[]> {
    innerValueType: string;
}

export interface RegistryListSetting extends ListSetting {
    registry: string;
}

export interface RegistryMutableListSetting extends Setting<string[]> {
    registry: string;
}

export interface ItemListSetting extends ListSetting {
    items: NamedItem[];
}

export interface NamedItem {
    name: string;
    value: string;
    icon: string | undefined;
}

export interface ConfigurableSetting extends Setting<ModuleSetting[]> {
}

export interface TogglableSetting extends Setting<ModuleSetting[]> {
}

export interface InputBind {
    boundKey: string;
    action: BindAction;
    modifiers: BindModifier[];
}

export type BindAction = "Toggle" | "Hold" | "Smart";

export type BindModifier = "Shift" | "Control" | "Alt" | "Super";
