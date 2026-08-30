type MouseKeyName =
    | "left"
    | "right"
    | "middle"
    | "4"
    | "5"
    | "6"
    | "7"
    | "8";

type KeyboardKeyName =
    | "unknown"
    | "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"
    | "a" | "b" | "c" | "d" | "e" | "f" | "g" | "h" | "i" | "j"
    | "k" | "l" | "m" | "n" | "o" | "p" | "q" | "r" | "s" | "t"
    | "u" | "v" | "w" | "x" | "y" | "z"
    | `f${1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25}`
    | "escape"
    | "enter"
    | "tab"
    | "space"
    | "backspace"
    | "caps.lock"
    | "left.shift" | "right.shift"
    | "left.control" | "right.control"
    | "left.alt" | "right.alt"
    | "left.win" | "right.win"
    | "menu"
    | "print.screen"
    | "scroll.lock"
    | "pause"
    | "insert"
    | "delete"
    | "home"
    | "end"
    | "page.up" | "page.down"
    | "up" | "down" | "left" | "right"
    | "num.lock"
    | "keypad.0" | "keypad.1" | "keypad.2" | "keypad.3"
    | "keypad.4" | "keypad.5" | "keypad.6" | "keypad.7"
    | "keypad.8" | "keypad.9"
    | "keypad.add" | "keypad.subtract"
    | "keypad.multiply" | "keypad.divide"
    | "keypad.enter" | "keypad.decimal" | "keypad.equal"
    | "semicolon" | "equal" | "comma"
    | "minus" | "period" | "slash"
    | "grave.accent" | "left.bracket" | "backslash"
    | "right.bracket" | "apostrophe"
    | "world.1" | "world.2";

export type MinecraftMouseKey = `key.mouse.${MouseKeyName}`;
export type MinecraftKeyboardKey = `key.keyboard.${KeyboardKeyName}`;
export type MinecraftKey = MinecraftMouseKey | MinecraftKeyboardKey;
