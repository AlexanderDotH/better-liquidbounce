<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import Modal from "../common/modal/Modal.svelte";
    import IconTextInput from "../common/setting/IconTextInput.svelte";
    import ButtonSetting from "../common/setting/ButtonSetting.svelte";

    export let visible: boolean;
    export let loading = false;

    const dispatch = createEventDispatcher<{
        generate: { apiKey: string };
    }>();

    let apiKey = "";
    let wasVisible = false;

    $: trimmedApiKey = apiKey.trim();
    $: disabled = trimmedApiKey.length === 0 || loading;
    $: {
        if (wasVisible && !visible) {
            cleanUp();
        }
        wasVisible = visible;
    }

    function generate() {
        if (disabled) {
            return;
        }

        dispatch("generate", {apiKey: trimmedApiKey});
    }

    function cleanUp() {
        apiKey = "";
    }
</script>

<Modal bind:visible={visible} title="TheAltening API Key" on:close={cleanUp}>
    <IconTextInput title="API Key" icon="lock" type="password" bind:value={apiKey}/>
    <ButtonSetting title="Generate" on:click={generate} {disabled} listenForEnter={true} inset={true} {loading}/>
</Modal>
