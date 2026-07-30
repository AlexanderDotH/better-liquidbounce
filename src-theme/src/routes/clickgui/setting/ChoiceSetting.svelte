<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import type {ChoiceSetting, ModuleSetting,} from "../../../integration/types";
    import ExpandArrow from "./common/ExpandArrow.svelte";
    import GenericSetting from "./common/GenericSetting.svelte";
    import {setItem} from "../../../integration/persistent_storage";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import Dropdown from "./common/Dropdown.svelte";

    export let setting: ModuleSetting;
    export let path: string;

    const cSetting = setting as ChoiceSetting;
    const thisPath = `${path}.${cSetting.name}`;

    const dispatch = createEventDispatcher();
    const allOptions = Object.keys(cSetting.choices);
    const categories: Record<string, string[]> = cSetting.categories ?? {};
    const categoryOptions = Object.keys(categories);
    let activeCategory = categoryOptions.find((category) =>
        categories[category].includes(cSetting.active)
    ) ?? categoryOptions[0];
    let options = allOptions;
    $: options = activeCategory ? categories[activeCategory] ?? allOptions : allOptions;
    const extendedDescriptions = Object.fromEntries(
        allOptions.map((option) => [option, cSetting.choices[option]?.extendedDescription]),
    );
    let expanded = localStorage.getItem(thisPath) === "true";

    let nestedSettings = cSetting.choices[cSetting.active]
        .value as ModuleSetting[];
    $: nestedSettings = cSetting.choices[cSetting.active]
        .value as ModuleSetting[];

    $: setItem(thisPath, expanded.toString());

    function handleChange() {
        setting = { ...cSetting };
        dispatch("change");
    }

    function handleCategoryChange() {
        const firstMode = categories[activeCategory]?.[0];
        if (!firstMode) {
            return;
        }

        cSetting.active = firstMode;
        handleChange();
    }

    function toggleExpanded() {
        expanded = !expanded;
    }
</script>

<div class="setting">
    {#if categoryOptions.length > 0}
        <div class="category">
            <Dropdown
                bind:value={activeCategory}
                options={categoryOptions}
                name="Category"
                on:change={handleCategoryChange}
            />
        </div>
    {/if}

    {#if nestedSettings.length > 0}
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <div class="head expand" class:expanded on:contextmenu|preventDefault={toggleExpanded}>
            <Dropdown
                bind:value={cSetting.active}
                {options}
                {extendedDescriptions}
                name={$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}
                on:change={handleChange}
            />
            <ExpandArrow bind:expanded />
        </div>
    {:else}
        <div class="head">
            <Dropdown
                bind:value={cSetting.active}
                {options}
                {extendedDescriptions}
                name={$spaceSeperatedNames ? convertToSpacedString(cSetting.name) : cSetting.name}
                on:change={handleChange}
            />
        </div>
    {/if}

    {#if expanded && nestedSettings.length > 0}
        <div class="nested-settings">
            {#each nestedSettings as setting (setting.name)}
                <GenericSetting path={thisPath} bind:setting={setting} on:change={handleChange} />
            {/each}
        </div>
    {/if}
</div>

<style lang="scss">

    .setting {
        padding: var(--clickgui-setting-padding, 7px 0px);

        .category {
            margin-bottom: var(--clickgui-setting-group-padding, 7px);
        }

        .head {
          transition: ease margin-bottom var(--clickgui-setting-transition-duration, .2s);

          &.expand {
              display: grid;
              grid-template-columns: 1fr max-content;
          }

          &.expanded {
              margin-bottom: var(--clickgui-setting-expanded-gap, 10px);
          }
        }
    }
    .nested-settings {
        border-left: solid var(--clickgui-setting-group-border-width, 2px) var(--clickgui-setting-group-border-color);
        padding-left: var(--clickgui-setting-group-padding, 7px);
    }
</style>
