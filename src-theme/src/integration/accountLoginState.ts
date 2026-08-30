import {writable} from "svelte/store";
import {listenAlways} from "./ws";

export const isLoggingIn = writable(false);

listenAlways("accountManagerLogin", () => isLoggingIn.set(false));
