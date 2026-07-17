import {get} from "svelte/store";
import {
    description as descriptionStore,
    scaleFactor,
    shiftHeld,
    type TDescription,
} from "../../clickgui_store";

export interface ShiftDescriptionParams {
    getText: () => string | undefined;
}

function computePosition(element: HTMLElement): Pick<TDescription, "x" | "y" | "anchor"> {
    const boundingRect = element.getBoundingClientRect();
    const sf = get(scaleFactor);
    const y = (boundingRect.top + (element.clientHeight / 2)) * (2 / sf);

    if (window.innerWidth - boundingRect.right > 300) {
        return {
            x: boundingRect.right * (2 / sf),
            y,
            anchor: "right",
        };
    }

    return {
        x: boundingRect.left * (2 / sf),
        y,
        anchor: "left",
    };
}

export function shiftDescription(node: HTMLElement, params: ShiftDescriptionParams) {
    let currentParams = params;
    let ownsDescription = false;
    let isHovering = false;

    function clearIfOwned() {
        if (!ownsDescription) {
            return;
        }

        descriptionStore.set(null);
        ownsDescription = false;
    }

    function updateDescription() {
        if (!isHovering || !get(shiftHeld)) {
            clearIfOwned();
            return;
        }

        const text = currentParams.getText();
        if (!text) {
            clearIfOwned();
            return;
        }

        descriptionStore.set({
            ...computePosition(node),
            description: text,
            variant: "extended",
        });
        ownsDescription = true;
    }

    function onMouseEnter(event: MouseEvent) {
        isHovering = true;
        if (event.shiftKey) {
            shiftHeld.set(true);
        }
        updateDescription();
    }

    function onMouseLeave() {
        isHovering = false;
        clearIfOwned();
    }

    const unsubscribeShiftHeld = shiftHeld.subscribe(updateDescription);

    node.addEventListener("mouseenter", onMouseEnter);
    node.addEventListener("mouseleave", onMouseLeave);

    return {
        update(newParams: ShiftDescriptionParams) {
            currentParams = newParams;
            updateDescription();
        },
        destroy() {
            node.removeEventListener("mouseenter", onMouseEnter);
            node.removeEventListener("mouseleave", onMouseLeave);
            unsubscribeShiftHeld();
            clearIfOwned();
        },
    };
}
