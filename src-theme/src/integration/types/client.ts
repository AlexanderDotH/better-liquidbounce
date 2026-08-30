import type {InputBind} from "./settings";

export interface Metadata {
    id: string;
    name: string;
    version: string;
    authors: string[];
    colors: {
        Accent: string;
        Tint: string;
    }
    screens: string[];
    overlays: string[];
    components: string[];
    fonts: string[];
    backgrounds: {
        name: string;
        types: string[];
    }[];
}

export interface Module {
    name: string;
    category: string;
    keyBind: InputBind;
    enabled: boolean;
    description: string;
    hasSettings: boolean;
    hidden: boolean;
    aliases: string[];
    tag: string | null;
}

export interface GroupedModules {
    [category: string]: Module[]
}
