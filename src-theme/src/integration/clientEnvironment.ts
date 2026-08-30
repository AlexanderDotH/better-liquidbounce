import {type Writable, writable} from "svelte/store";

export const os: Writable<string | null> = writable<string | null>(null);
