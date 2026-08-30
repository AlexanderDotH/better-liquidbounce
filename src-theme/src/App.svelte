<script lang="ts">
    import Router, {location, push} from "svelte-spa-router";
    import Hud from "./routes/hud/Hud.svelte";
    import {getMetadata, getTheme, getVirtualScreen} from "./integration/rest";
    import {cleanupListeners, listenAlways} from "./integration/ws";
    import {onMount} from "svelte";
    import {insertPersistentData} from "./integration/persistent_storage";
    import {isStatic} from "./integration/host";
    import Inventory from "./routes/inventory/Inventory.svelte";
    import Title from "./routes/menu/title/Title.svelte";
    import Multiplayer from "./routes/menu/multiplayer/Multiplayer.svelte";
    import AltManager from "./routes/menu/altmanager/AltManager.svelte";
    import Singleplayer from "./routes/menu/singleplayer/Singleplayer.svelte";
    import ProxyManager from "./routes/menu/proxymanager/ProxyManager.svelte";
    import None from "./routes/none/None.svelte";
    import Disconnected from "./routes/menu/disconnected/Disconnected.svelte";
    import Browser from "./routes/browser/Browser.svelte";
    import ClickGuiThemeHost from "./routes/clickgui/theme/ClickGuiThemeHost.svelte";
    import BaritoneDashboard from "./routes/baritone/BaritoneDashboard.svelte";
    import {intToRgba, rgbaToHex} from "./integration/util";
    import type {ThemeColorChangeEvent} from "./integration/events";
    import Menu from "./routes/menu/common/Menu.svelte";
    import MenuContent from "./routes/menu/common/MenuContent.svelte";
    import {releaseDocumentFocus} from "./util/documentFocus";

    const menuRoutes = {
        "/title": Title,
        "/multiplayer": Multiplayer,
        "/altmanager": AltManager,
        "/singleplayer": Singleplayer,
        "/proxymanager": ProxyManager,
    };

    const routes = {
        "/clickgui": ClickGuiThemeHost,
        "/baritone": BaritoneDashboard,
        "/hud": Hud,
        "/inventory": Inventory,
        "/none": None,
        "/disconnected": Disconnected,
        "/browser": Browser
    };

    const SURFACE_TINT_MIX = 18;

    function isMenuRoute(route: string): boolean {
        return route in menuRoutes;
    }

    async function changeRoute(name: string) {
        const nextRoute = `/${name}`;
        if (!isMenuRoute($location) || !isMenuRoute(nextRoute)) {
            cleanupListeners();
        }

        console.log(`[Router] Redirecting to ${name}`);
        await push(nextRoute);
    }

    function setThemeColor(name: string, value: string) {
        document.documentElement.style.setProperty(`--${name}`, value);
    }

    function themeColorToHex(value: number) {
        return rgbaToHex(intToRgba(value));
    }

    function mixColors(leftColor: string, rightColor: string, strength: number) {
        return `color-mix(in srgb, ${leftColor} ${100 - strength}%, ${rightColor})`;
    }

    function applyAccentColor(color: number) {
        setThemeColor("accent-color", themeColorToHex(color));
    }

    function applyTintColor(defaultSurfaceColor: string, color: number) {
        setThemeColor("surface-color", mixColors(defaultSurfaceColor, themeColorToHex(color), SURFACE_TINT_MIX));
    }

    async function navigateToVirtualScreen() {
        const virtualScreen = await getVirtualScreen();
        await changeRoute(virtualScreen.name || "none");
    }

    async function handleVirtualScreen(event: any) {
        console.log(`[Router] Virtual screen change to ${event.screenName}`);
        if (event.action === "close") {
            releaseDocumentFocus();
            await changeRoute("none");
            return;
        }
        if (event.action === "open") {
            await changeRoute(event.screenName || "none");
        }
    }

    function registerThemeListener(themeId: string, defaultSurfaceColor: string) {
        listenAlways("themeColorChange", async (event: ThemeColorChangeEvent) => {
            if (event.themeId !== themeId) return;
            if (event.name === "Accent") applyAccentColor(event.value);
            if (event.name === "Tint") applyTintColor(defaultSurfaceColor, event.value);
        });
    }

    async function initializeApplication() {
        const metadata = await getMetadata();
        const defaultSurfaceColor = metadata.colors.Tint;
        const theme = await getTheme(metadata.id);
        applyAccentColor(theme.colors.accent);
        applyTintColor(defaultSurfaceColor, theme.colors.tint);
        await insertPersistentData();
        registerThemeListener(metadata.id, defaultSurfaceColor);
        if (isStatic) return;
        listenAlways("socketReady", navigateToVirtualScreen);
        listenAlways("virtualScreen", handleVirtualScreen);
        await navigateToVirtualScreen();
    }

    onMount(initializeApplication);
</script>

<main>
    {#if isMenuRoute($location)}
        <Menu>
            {#key $location}
                <MenuContent>
                    <Router routes={menuRoutes}/>
                </MenuContent>
            {/key}
        </Menu>
    {:else}
        <Router {routes}/>
    {/if}
</main>
