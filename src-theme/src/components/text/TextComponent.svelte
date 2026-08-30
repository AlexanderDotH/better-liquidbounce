<script lang="ts">
    import type {TextComponent as TTextComponent} from "../../integration/types";
    import {convertLegacyCodes, translateTextColor} from "./legacyTextFormatting.ts";

    export let textComponent: TTextComponent | string;
    export let allowPreformatting = false;
    export let preFormattingMonospace = true;
    export let inheritedColor = "#ffffff";
    export let inheritedStrikethrough = false;
    export let inheritedItalic = false;
    export let inheritedUnderlined = false;
    export let inheritedBold = false;
    export let fontSize: number;

</script>

<span class="text-component">
    {#if typeof textComponent === "string"}
        <svelte:self {fontSize} {allowPreformatting} {preFormattingMonospace} textComponent={convertLegacyCodes(textComponent)}/>
    {:else if textComponent}
        {#if textComponent.text}
            {#if !textComponent.text.includes("§")}
                <span class="text" class:bold={textComponent.bold !== undefined ? textComponent.bold : inheritedBold}
                      class:italic={textComponent.italic !== undefined ? textComponent.italic : inheritedItalic}
                      class:underlined={textComponent.underlined !== undefined ? textComponent.underlined : inheritedUnderlined}
                      class:strikethrough={textComponent.strikethrough !== undefined ? textComponent.strikethrough : inheritedStrikethrough}
                      class:allow-preformatting={allowPreformatting}
                      class:monospace={preFormattingMonospace && allowPreformatting}
                      style="color: {textComponent.color !== undefined ? translateTextColor(textComponent.color) : translateTextColor(inheritedColor)}; font-size: {fontSize}px;">{textComponent.text}</span>
            {:else}
                <svelte:self {allowPreformatting} {preFormattingMonospace} {fontSize}
                             inheritedColor={textComponent.color !== undefined ? textComponent.color : inheritedColor}
                             inheritedBold={textComponent.bold !== undefined ? textComponent.bold : inheritedBold}
                             inheritedItalic={textComponent.italic !== undefined ? textComponent.italic : inheritedItalic}
                             inheritedUnderlined={textComponent.underlined !== undefined ? textComponent.underlined : inheritedUnderlined}
                             inheritedStrikethrough={textComponent.strikethrough !== undefined ? textComponent.strikethrough : inheritedStrikethrough}
                             textComponent={convertLegacyCodes(textComponent.text)}/>
            {/if}
        {/if}
        {#if textComponent.extra}
            {#each textComponent.extra as e}
                <svelte:self {allowPreformatting} {preFormattingMonospace} {fontSize}
                             inheritedColor={textComponent.color !== undefined ? textComponent.color : inheritedColor}
                             inheritedBold={textComponent.bold !== undefined ? textComponent.bold : inheritedBold}
                             inheritedItalic={textComponent.italic !== undefined ? textComponent.italic : inheritedItalic}
                             inheritedUnderlined={textComponent.underlined !== undefined ? textComponent.underlined : inheritedUnderlined}
                             inheritedStrikethrough={textComponent.strikethrough !== undefined ? textComponent.strikethrough : inheritedStrikethrough}
                             textComponent={e}/>
            {/each}
        {/if}
    {/if}
</span>

<style>
    .text-component {
        font-size: 0;
    }

    .text {
        display: inline;

        &.allow-preformatting {
            white-space: pre;
        }

        &.monospace {
            font-family: monospace;
        }

        &.bold {
            font-weight: 500;
        }

        &.italic {
            font-style: italic;
        }

        &.underlined {
            text-decoration: underline;
        }

        &.strikethrough {
            text-decoration: line-through;
        }
    }
</style>
