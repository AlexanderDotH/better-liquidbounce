import type {BaritoneRestClient, BaritoneSubscribe, BaritoneViewState} from "./types";
import {
    applyBaritoneLogEvent,
    applyBaritoneRouteEvent,
    applyBaritoneStateEvent,
    createInitialBaritoneViewState,
} from "./state.ts";

interface BaritoneDataSourceDependencies {
    client: Pick<BaritoneRestClient, "getSnapshot" | "getRoute">;
    subscribe: BaritoneSubscribe;
    onChange: (state: BaritoneViewState) => void;
}

export function createBaritoneDataSource(dependencies: BaritoneDataSourceDependencies) {
    return new BaritoneDataSource(dependencies);
}

class BaritoneDataSource {
    private state = createInitialBaritoneViewState();
    private refreshGeneration = 0;
    private readonly disposers: Array<() => void>;
    private readonly dependencies: BaritoneDataSourceDependencies;

    constructor(dependencies: BaritoneDataSourceDependencies) {
        this.dependencies = dependencies;
        const {subscribe} = dependencies;
        this.disposers = [
            subscribe("socketReady", () => this.refresh()),
            subscribe("baritoneState", event => this.update(applyBaritoneStateEvent(this.state, event))),
            subscribe("baritoneRoute", event => this.update(applyBaritoneRouteEvent(this.state, event))),
            subscribe("baritoneLog", event => this.update(applyBaritoneLogEvent(this.state, event))),
        ].filter((dispose): dispose is () => void => typeof dispose === "function");
    }

    async refresh(): Promise<void> {
        const generation = ++this.refreshGeneration;
        const [snapshot, route] = await Promise.all([
            this.dependencies.client.getSnapshot(),
            this.dependencies.client.getRoute(),
        ]);
        if (generation !== this.refreshGeneration) return;
        let nextState = applyBaritoneStateEvent(this.state, {
            revision: snapshot.revision,
            snapshot,
        });
        nextState = applyBaritoneRouteEvent(nextState, {
            revision: route.revision,
            route,
        });
        this.update(nextState);
    }

    getState(): BaritoneViewState {
        return this.state;
    }

    stop(): void {
        this.disposers.forEach(dispose => dispose());
    }

    private update(nextState: BaritoneViewState): void {
        if (nextState === this.state) return;
        this.state = nextState;
        this.dependencies.onChange(this.state);
    }
}
