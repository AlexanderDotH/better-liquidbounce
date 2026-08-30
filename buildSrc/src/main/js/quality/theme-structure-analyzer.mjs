import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'
import { analyzeEstreeFunctions } from './estree-function-metrics.mjs'
import { analyzeTypeScriptFunctions } from './typescript-function-metrics.mjs'

const repositoryRoot = process.argv[2]
const input = JSON.parse(readFileSync(0, 'utf8'))
const typescript = await loadDefault('src-theme/node_modules/typescript/lib/typescript.js')
const svelte = await loadDefault('src-theme/node_modules/svelte/compiler/index.js')
const results = []

for (const file of input.files) {
    if (file.path.endsWith('.svelte')) {
        const ast = svelte.parse(file.content, { modern: true, filename: file.path })
        for (const program of [ast.instance?.content, ast.module?.content].filter(Boolean)) {
            results.push(...analyzeEstreeFunctions(file, program))
        }
    } else {
        results.push(...analyzeTypeScriptFunctions(typescript, file))
    }
}

results.sort((left, right) =>
    left.path.localeCompare(right.path) || left.line - right.line ||
    left.name.localeCompare(right.name) || left.metric.localeCompare(right.metric),
)
process.stdout.write(JSON.stringify(results))

async function loadDefault(relativePath) {
    const url = pathToFileURL(resolve(repositoryRoot, relativePath)).href
    const imported = await import(url)
    return imported.default ?? imported
}
