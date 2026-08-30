import type {Vec3} from "./gameplay";

export interface BedState {
    block: string;
    trackedBlockPos: Vec3;
    pos: Vec3;
    surroundingBlocks: SurroundingBlock[];
    compactSurroundingBlocks: SurroundingBlock[];
}

export interface SurroundingBlock {
    block: string;
    count: number;
    layer: number;
}
