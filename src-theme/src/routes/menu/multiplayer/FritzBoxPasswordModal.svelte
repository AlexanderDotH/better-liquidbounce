<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import Modal from "../common/modal/Modal.svelte";
    import IconTextInput from "../common/setting/IconTextInput.svelte";
    import ButtonSetting from "../common/setting/ButtonSetting.svelte";

    export let visible: boolean;

    const dispatch = createEventDispatcher<{
        reconnect: { password: string };
    }>();

    let password = "";

    $: disabled = password.length === 0;

    function reconnect() {
        if (disabled) {
            return;
        }

        dispatch("reconnect", {password});
        cleanUp();
        visible = false;
    }

    function cleanUp() {
        password = "";
    }
</script>

<Modal bind:visible={visible} title="FritzBox Password" on:close={cleanUp}>
    <IconTextInput title="Password" icon="lock" type="password" bind:value={password}/>
    <ButtonSetting title="Reconnect" on:click={reconnect} {disabled} listenForEnter={true} inset={true}/>
</Modal>
