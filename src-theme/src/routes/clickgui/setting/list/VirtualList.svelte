<!-- Adapted from https://github.com/sveltejs/svelte-virtual-list -->
<script lang="ts">
    import {onMount, tick} from 'svelte';
    // props
    export let items: any[];
    export let height = '100%';
    export let itemHeight: number | undefined = undefined;
    // read-only, but visible to consumers via bind:start
    export let start = 0;
    export let end = 0;
    // local state
    let height_map: number[] = [];
    let rows: HTMLCollectionOf<HTMLElement>;
    let viewport: HTMLElement;
    let contents: HTMLElement;
    let viewport_height = 0;
    let visible: {index: number, data: any}[];
    let mounted = false;
    let top = 0;
    let bottom = 0;
    let average_height = 0;
    $: visible = items.slice(start, end).map((data, i) => {
        return { index: i + start, data };
    });
    // whenever `items` changes, invalidate the current heightmap
    $: if (mounted) refresh(items, viewport_height, itemHeight);
    async function refresh(items: any[], viewport_height: number, itemHeight: number | undefined) {
        const { scrollTop } = viewport;
        await tick(); // wait until the DOM is up to date
        let content_height = top - scrollTop;
        let i = start;
        while (content_height < viewport_height && i < items.length) {
            let row = rows[i - start];
            if (!row) {
                end = i + 1;
                await tick(); // render the newly visible row
                row = rows[i - start];
            }
            const row_height = height_map[i] = itemHeight || row.offsetHeight;
            content_height += row_height;
            i += 1;
        }
        end = i;
        const remaining = items.length - end;
        average_height = (top + content_height) / end;
        bottom = remaining * average_height;
        height_map.length = items.length;

        setTimeout(() => {
            viewport.scrollTop = 0;
        }, 100);
    }
    async function handle_scroll() {
        const { scrollTop } = viewport;
        const old_start = start;
        measureVisibleRows();
        const visibleStart = findVisibleStart(scrollTop);
        start = visibleStart.startIndex;
        top = visibleStart.topHeight;
        const visibleEnd = findVisibleEnd(visibleStart, scrollTop);
        end = visibleEnd.index;
        const remaining = items.length - end;
        average_height = visibleEnd.height / end;
        fillUnknownHeights(visibleEnd.index);
        bottom = remaining * average_height;
        if (start < old_start) await compensateScrollUp(old_start, scrollTop);
        // TODO if we overestimated the space these rows would occupy we may need to add some more.
    }
    function measureVisibleRows() {
        for (let v = 0; v < rows.length; v += 1) {
            height_map[start + v] = itemHeight || rows[v].offsetHeight;
        }
    }
    function findVisibleStart(scrollTop: number) {
        let i = 0;
        let y = 0;
        let startIndex = start;
        let topHeight = top;
        while (i < items.length) {
            const row_height = height_map[i] || average_height;
            if (y + row_height > scrollTop) {
                startIndex = i;
                topHeight = y;
                break;
            }
            y += row_height;
            i += 1;
        }
        return {startIndex, topHeight, cursor: i, height: y};
    }
    function findVisibleEnd(visibleStart: {cursor: number, height: number}, scrollTop: number) {
        let i = visibleStart.cursor;
        let y = visibleStart.height;
        while (i < items.length) {
            y += height_map[i] || average_height;
            i += 1;
            if (y > scrollTop + viewport_height) break;
        }
        return {index: i, height: y};
    }
    function fillUnknownHeights(index: number) {
        while (index < items.length) height_map[index++] = average_height;
    }
    async function compensateScrollUp(oldStart: number, scrollTop: number) {
        await tick();
        let expected_height = 0;
        let actual_height = 0;
        for (let i = start; i < oldStart; i +=1) {
            if (rows[i - start]) {
                expected_height += height_map[i];
                actual_height += itemHeight || rows[i - start].offsetHeight;
            }
        }
        viewport.scrollTo(0, scrollTop + actual_height - expected_height);
    }
    // trigger initial refresh
    onMount(() => {
        rows = contents.getElementsByTagName('svelte-virtual-list-row') as HTMLCollectionOf<HTMLElement>;
        mounted = true;
    });
</script>

<style>
    svelte-virtual-list-viewport {
        position: relative;
        overflow-y: auto;
        -webkit-overflow-scrolling:touch;
        display: block;
    }
    svelte-virtual-list-contents, svelte-virtual-list-row {
        display: block;
    }
    svelte-virtual-list-row {
        overflow: hidden;
    }
</style>

<svelte-virtual-list-viewport
        bind:this={viewport}
        bind:offsetHeight={viewport_height}
        on:scroll={handle_scroll}
        style="height: {height};"
>
    <svelte-virtual-list-contents
            bind:this={contents}
            style="padding-top: {top}px; padding-bottom: {bottom}px;"
    >
        {#each visible as row (row.index)}
            <svelte-virtual-list-row>
                <slot item={row.data}>Missing template</slot>
            </svelte-virtual-list-row>
        {/each}
    </svelte-virtual-list-contents>
</svelte-virtual-list-viewport>
