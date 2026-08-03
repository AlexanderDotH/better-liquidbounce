<script lang="ts">
    import Header from "./header/Header.svelte";
    import {fly} from "svelte/transition";
    import {onMount} from "svelte";
    import {
        MENU_READY_DELAY_MS,
        MENU_TRANSITION_MS,
        MENU_VERTICAL_OFFSET_PX,
    } from "./menuMotion";

    let ready = false;

    onMount(() => {
        setTimeout(() => {
            ready = true;
        }, MENU_READY_DELAY_MS);
    });
</script>

<div class="menu">
    {#if ready}
        <div transition:fly|global={{duration: MENU_TRANSITION_MS, y: -MENU_VERTICAL_OFFSET_PX}}>
            <Header/>
        </div>
    {/if}

    <div class="menu-wrapper">
        <slot/>
    </div>
</div>

<style lang="scss">
  .menu {
    padding: 50px;
    display: flex;
    flex-direction: column;
    height: 100vh;
  }

  .menu-wrapper {
    flex: 1;
    display: flex;
    flex-direction: column;
    will-change: transform;
  }

  @media screen and (max-width: 1366px) {
    .menu {
      zoom: 0.8;
      height: 125vh;
    }
  }

  @media screen and (max-width: 1200px) {
    .menu {
      zoom: 0.5;
      height: 200vh;
    }
  }

  @media screen and (max-height: 1100px) {
    .menu {
      zoom: 0.8;
      height: 125vh;
    }
  }

  @media screen and (max-height: 700px) {
    .menu {
      zoom: 0.5;
      height: 200vh;
    }
  }

  @media screen and (max-height: 540px) {
    .menu {
      zoom: 0.4;
      height: 250vh;
    }
  }
</style>
