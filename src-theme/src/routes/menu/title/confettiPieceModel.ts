export interface ConfettiPiece {
    id: number;
    shapeClass: string;
    style: string;
}

const COLORS = Array.from({length: 7}, (_, index) => `var(--confetti-color-${index + 1})`);
const PIECE_COUNT = 84;
const COLUMN_COUNT = 42;
const HORIZONTAL_PADDING = -8;
const HORIZONTAL_SPREAD = 116;
const GOLDEN_STEP = 0.61803398875;

export function createConfettiPieces(): ConfettiPiece[] {
    return Array.from({length: PIECE_COUNT}, (_, index) => createPiece(index));
}

function createPiece(index: number): ConfettiPiece {
    const shape = createShape(index);
    const position = createPosition(index);
    const motion = createMotion(index);
    return {
        id: index,
        shapeClass: shape.className,
        style: createStyle(shape, position, motion),
    };
}

function createShape(index: number) {
    const shapeValue = random(index + 11);
    const baseSize = 6 + random(index + 23) * 12;
    const isRound = shapeValue > 0.7;
    const isStreamer = shapeValue < 0.2;
    return {
        className: isRound ? "is-round" : isStreamer ? "is-streamer" : "",
        width: isRound ? baseSize : isStreamer ? baseSize * 0.45 : baseSize * 0.72,
        height: isRound ? baseSize : isStreamer ? baseSize * 1.8 : baseSize * 1.24,
        borderRadius: isRound ? "999px" : `${1 + random(index + 31) * 4}px`,
    };
}

function createPosition(index: number) {
    const columnWidth = HORIZONTAL_SPREAD / COLUMN_COUNT;
    const column = index % COLUMN_COUNT;
    const layer = Math.floor(index / COLUMN_COUNT);
    const jitter = (random(index + 67) - 0.5) * columnWidth * 0.8;
    const direction = (column + layer) % 2 === 0 ? -1 : 1;
    return {
        left: HORIZONTAL_PADDING + (column + 0.5) * columnWidth + jitter,
        drift: direction * (34 + random(index + 79) * 74) + (random(index + 149) - 0.5) * 12,
    };
}

function createMotion(index: number) {
    const duration = 7 + random(index + 41) * 9;
    const delayProgress = fractional(index * GOLDEN_STEP + random(index + 53) * 0.2);
    return {
        duration,
        delay: -(delayProgress * duration),
        opacity: 0.45 + random(index + 83) * 0.35,
        flip: 240 + random(index + 97) * 540,
        spinDuration: 1.8 + random(index + 101) * 2.8,
        color: COLORS[Math.floor(random(index + 113) * COLORS.length)],
        scale: 0.75 + random(index + 127) * 0.85,
        blur: random(index + 137) > 0.86 ? 1 : 0,
    };
}

function createStyle(shape: ReturnType<typeof createShape>, position: ReturnType<typeof createPosition>, motion: ReturnType<typeof createMotion>): string {
    return [
        `--left:${position.left}%;`,
        `--drift:${position.drift}px;`,
        `--fall-duration:${motion.duration.toFixed(2)}s;`,
        `--fall-delay:${motion.delay.toFixed(2)}s;`,
        `--spin-duration:${motion.spinDuration.toFixed(2)}s;`,
        `--width:${shape.width.toFixed(2)}px;`,
        `--height:${shape.height.toFixed(2)}px;`,
        `--opacity:${motion.opacity.toFixed(2)};`,
        `--color:${motion.color};`,
        `--flip:${motion.flip.toFixed(0)}deg;`,
        `--scale:${motion.scale.toFixed(2)};`,
        `--blur:${motion.blur}px;`,
        `--border-radius:${shape.borderRadius};`,
    ].join("");
}

function random(seed: number): number {
    const value = Math.sin(seed * 12.9898) * 43758.5453;
    return value - Math.floor(value);
}

function fractional(value: number): number {
    return value - Math.floor(value);
}
