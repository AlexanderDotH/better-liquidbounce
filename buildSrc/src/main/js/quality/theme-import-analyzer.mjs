import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'
import { resolve } from 'node:path'

const repositoryRoot = process.argv[2]
const input = JSON.parse(readFileSync(0, 'utf8'))
const typescript = await loadDefault('src-theme/node_modules/typescript/lib/typescript.js')
const svelte = await loadDefault('src-theme/node_modules/svelte/compiler/index.js')
const references = input.files.flatMap(file => importsIn(file))

references.sort((left, right) =>
    left.path.localeCompare(right.path) || left.line - right.line || left.specifier.localeCompare(right.specifier),
)
process.stdout.write(JSON.stringify(references))

function importsIn(file) {
    if (file.path.endsWith('.svelte')) return svelteImports(file)
    return typeScriptImports(file)
}

function typeScriptImports(file) {
    const source = typescript.createSourceFile(
        file.path,
        file.content,
        typescript.ScriptTarget.Latest,
        true,
        scriptKind(file.path),
    )
    const results = []
    visitTypeScript(source, node => {
        const specifier = typeScriptSpecifier(node)
        if (!specifier) return
        const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1
        results.push({ path: file.path, line, specifier })
    })
    return results
}

function visitTypeScript(node, consumer) {
    consumer(node)
    typescript.forEachChild(node, child => visitTypeScript(child, consumer))
}

function typeScriptSpecifier(node) {
    if ((typescript.isImportDeclaration(node) || typescript.isExportDeclaration(node)) && node.moduleSpecifier) {
        return node.moduleSpecifier.text
    }
    if (!typescript.isCallExpression(node) || node.expression.kind !== typescript.SyntaxKind.ImportKeyword) return null
    return typescript.isStringLiteralLike(node.arguments[0]) ? node.arguments[0].text : null
}

function svelteImports(file) {
    const ast = svelte.parse(file.content, { modern: true, filename: file.path })
    const results = []
    for (const program of [ast.instance?.content, ast.module?.content].filter(Boolean)) {
        visitEstree(program, node => {
            const specifier = estreeSpecifier(node)
            if (!specifier) return
            results.push({ path: file.path, line: node.loc?.start?.line ?? lineAt(file.content, node.start), specifier })
        })
    }
    return results
}

function visitEstree(node, consumer) {
    if (!node || typeof node !== 'object') return
    consumer(node)
    for (const [key, value] of Object.entries(node)) {
        if (['end', 'loc', 'metadata', 'start'].includes(key)) continue
        if (Array.isArray(value)) value.filter(isNode).forEach(child => visitEstree(child, consumer))
        else if (isNode(value)) visitEstree(value, consumer)
    }
}

function estreeSpecifier(node) {
    if (['ImportDeclaration', 'ExportAllDeclaration', 'ExportNamedDeclaration'].includes(node.type)) {
        return node.source?.value ?? null
    }
    return node.type === 'ImportExpression' ? node.source?.value ?? null : null
}

function isNode(value) {
    return value && typeof value === 'object' && typeof value.type === 'string'
}

function scriptKind(path) {
    if (path.endsWith('.ts')) return typescript.ScriptKind.TS
    if (path.endsWith('.tsx')) return typescript.ScriptKind.TSX
    if (path.endsWith('.jsx')) return typescript.ScriptKind.JSX
    return typescript.ScriptKind.JS
}

function lineAt(content, offset) {
    return content.slice(0, offset).split(/\r?\n/u).length
}

async function loadDefault(relativePath) {
    const url = pathToFileURL(resolve(repositoryRoot, relativePath)).href
    const imported = await import(url)
    return imported.default ?? imported
}
