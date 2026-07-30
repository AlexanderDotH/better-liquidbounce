<script lang="ts">
    import {createEventDispatcher} from "svelte";
    import Modal from "../common/modal/Modal.svelte";
    import ButtonSetting from "../common/setting/ButtonSetting.svelte";

    export let visible = false;
    export let username: string;
    export let loading = false;

    const dispatch = createEventDispatcher<{
        confirm: void;
        cancel: void;
    }>();

    function cancel() {
        if (loading) {
            return;
        }

        visible = false;
        dispatch("cancel");
    }

    function confirm() {
        if (!loading) {
            dispatch("confirm");
        }
    }
</script>

<Modal bind:visible title="Delete Favorite Account" on:close={() => dispatch("cancel")}>
    <p>
        <strong>{username}</strong> is marked as a favorite. Are you sure you want to permanently delete this account?
    </p>

    <div class="actions">
        <ButtonSetting title="Cancel" secondary={true} disabled={loading} on:click={cancel}/>
        <ButtonSetting title="Delete Favorite" danger={true} disabled={loading} {loading} on:click={confirm}/>
    </div>
</Modal>

<style lang="scss">
  p {
    margin: 0 30px;
    color: var(--menu-text-color);
    font-size: 20px;
    line-height: 1.5;
    text-align: center;
  }

  .actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 15px;
    margin: 0 30px;
  }
</style>
