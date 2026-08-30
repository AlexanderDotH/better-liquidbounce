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
    const controller = new ShiftDescriptionController(node, params);
    controller.connect();
    return {
        update: (next: ShiftDescriptionParams) => controller.update(next),
        destroy: () => controller.destroy(),
    };
}

class ShiftDescriptionController {
    private readonly node: HTMLElement;
    private currentParams: ShiftDescriptionParams;
    private ownsDescription = false;
    private isHovering = false;
    private unsubscribeShiftHeld: () => void = () => undefined;

    constructor(node: HTMLElement, params: ShiftDescriptionParams) {
        this.node = node;
        this.currentParams = params;
    }

    connect(): void {
        this.unsubscribeShiftHeld = shiftHeld.subscribe(this.updateDescription);
        this.node.addEventListener("mouseenter", this.onMouseEnter);
        this.node.addEventListener("mouseleave", this.onMouseLeave);
    }

    update(params: ShiftDescriptionParams): void {
        this.currentParams = params;
        this.updateDescription();
    }

    destroy(): void {
        this.node.removeEventListener("mouseenter", this.onMouseEnter);
        this.node.removeEventListener("mouseleave", this.onMouseLeave);
        this.unsubscribeShiftHeld();
        this.clearIfOwned();
    }

    private clearIfOwned(): void {
        if (!this.ownsDescription) return;
        descriptionStore.set(null);
        this.ownsDescription = false;
    }

    private updateDescription = (): void => {
        if (!this.isHovering || !get(shiftHeld)) {
            this.clearIfOwned();
            return;
        }
        const text = this.currentParams.getText();
        if (!text) {
            this.clearIfOwned();
            return;
        }
        descriptionStore.set({
            ...computePosition(this.node),
            description: text,
            variant: "extended",
        });
        this.ownsDescription = true;
    };

    private onMouseEnter = (event: MouseEvent): void => {
        this.isHovering = true;
        if (event.shiftKey) shiftHeld.set(true);
        this.updateDescription();
    };

    private onMouseLeave = (): void => {
        this.isHovering = false;
        this.clearIfOwned();
    };
}
