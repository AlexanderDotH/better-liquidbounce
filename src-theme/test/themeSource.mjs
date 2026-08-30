import {existsSync, readFileSync} from "node:fs";
import {basename, dirname, extname, join, resolve} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";

function readUtf8(url) {
    return readFileSync(url, "utf8");
}

function existingModuleUrl(parentUrl, specifier) {
    if (!specifier.startsWith(".")) {
        return null;
    }

    const unresolved = fileURLToPath(new URL(specifier, parentUrl));
    const candidates = extname(unresolved)
        ? [unresolved]
        : [`${unresolved}.ts`, `${unresolved}.js`, `${unresolved}.mjs`];
    const match = candidates.find(existsSync);
    return match ? pathToFileURL(match) : null;
}

function existingStyleUrl(parentUrl, specifier) {
    if (!specifier.startsWith(".")) {
        return null;
    }

    const unresolved = fileURLToPath(new URL(specifier, parentUrl));
    const extension = extname(unresolved);
    const directory = dirname(unresolved);
    const filename = basename(unresolved);
    const candidates = [".css", ".sass", ".scss"].includes(extension)
        ? [unresolved, join(directory, `_${filename}`)]
        : [
            `${unresolved}.scss`,
            join(directory, `_${filename}.scss`),
        ];
    const match = candidates.find(existsSync);
    return match ? pathToFileURL(match) : null;
}

function collect(url, dependencyUrls, visited) {
    const path = fileURLToPath(url);
    if (visited.has(path)) {
        return "";
    }
    visited.add(path);

    const source = readUtf8(url);
    const dependencies = dependencyUrls(source, url).map(dependency =>
        collect(dependency, dependencyUrls, visited),
    );
    return [source, ...dependencies].join("\n");
}

function styleDependencies(source, url) {
    const specifiers = [
        ...source.matchAll(/@(?:use|forward)\s+["']([^"']+)["']/g),
        ...source.matchAll(/import\s+["']([^"']+\.(?:css|sass|scss))["']/g),
    ];
    return specifiers
        .map(match => existingStyleUrl(url, match[1]))
        .filter(Boolean);
}

function moduleDependencies(source, url) {
    return [...source.matchAll(/(?:from\s+|export\s+\*\s+from\s+)["']([^"']+)["']/g)]
        .map(match => existingModuleUrl(url, match[1]))
        .filter(Boolean);
}

function componentDependencies(source, url) {
    const directImports = [...source.matchAll(/import\s+["']([^"']+)["']/g)]
        .map(match => existingStyleUrl(url, match[1]))
        .filter(Boolean);
    return [
        ...moduleDependencies(source, url),
        ...styleDependencies(source, url),
        ...directImports,
    ];
}

export function readSourceWithStyles(url) {
    return collect(url, styleDependencies, new Set());
}

export function readModuleGraph(url) {
    return collect(url, moduleDependencies, new Set());
}

export function readComponentSourceWithStyles(url) {
    return collect(url, componentDependencies, new Set());
}

export function sourceUrl(rootUrl, relativePath) {
    const rootPath = fileURLToPath(rootUrl);
    return pathToFileURL(resolve(rootPath, relativePath));
}
