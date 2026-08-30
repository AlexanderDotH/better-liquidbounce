const FUNCTIONS = new Set(['ArrowFunctionExpression', 'FunctionDeclaration', 'FunctionExpression'])
const DECISIONS = new Set([
    'CatchClause',
    'ConditionalExpression',
    'DoWhileStatement',
    'ForInStatement',
    'ForOfStatement',
    'ForStatement',
    'SwitchStatement',
    'WhileStatement',
])

export function analyzeEstreeFunctions(file, program) {
    const results = []
    visit(program, null, (node, parent) => {
        if (!FUNCTIONS.has(node.type)) return
        const line = lineAt(file.content, node.start)
        const name = functionName(node, parent, line)
        const metrics = measure(node.body)
        results.push(
            { path: file.path, name, line, metric: 'method-lines', measured: effectiveLines(file.content, node.start, node.end) },
            { path: file.path, name, line, metric: 'cognitive-complexity', measured: metrics.complexity },
            { path: file.path, name, line, metric: 'nesting-depth', measured: metrics.nesting },
        )
    })
    return results
}

function measure(root) {
    const metrics = { complexity: 0, nesting: 0 }
    scan(root, root, 0, metrics)
    return metrics
}

function scan(node, root, depth, metrics) {
    if (!node || typeof node !== 'object') return
    if (node !== root && FUNCTIONS.has(node.type)) return
    if (node.type === 'IfStatement') return scanIf(node, root, depth, metrics)
    if (DECISIONS.has(node.type)) return scanDecision(node, root, depth, metrics)
    if (node.type === 'TryStatement') return scanTry(node, root, depth, metrics)
    if (node.type === 'LogicalExpression' && ['&&', '||', '??'].includes(node.operator)) metrics.complexity++
    scanChildren(node, root, depth, metrics)
}

function scanIf(node, root, depth, metrics) {
    recordDecision(metrics, depth)
    scan(node.test, root, depth, metrics)
    scan(node.consequent, root, depth + 1, metrics)
    if (node.alternate) scan(node.alternate, root, node.alternate.type === 'IfStatement' ? depth : depth + 1, metrics)
}

function scanDecision(node, root, depth, metrics) {
    recordDecision(metrics, depth)
    scanChildren(node, root, depth + 1, metrics)
}

function scanTry(node, root, depth, metrics) {
    metrics.nesting = Math.max(metrics.nesting, depth + 1)
    scanChildren(node, root, depth + 1, metrics)
}

function recordDecision(metrics, depth) {
    metrics.complexity += 1 + depth
    metrics.nesting = Math.max(metrics.nesting, depth + 1)
}

function scanChildren(node, root, depth, metrics) {
    children(node).forEach(child => scan(child, root, depth, metrics))
}

function visit(node, parent, consumer) {
    if (!node || typeof node !== 'object') return
    consumer(node, parent)
    children(node).forEach(child => visit(child, node, consumer))
}

function children(node) {
    return Object.entries(node).flatMap(([key, value]) => {
        if (['end', 'loc', 'metadata', 'start'].includes(key)) return []
        if (Array.isArray(value)) return value.filter(isNode)
        return isNode(value) ? [value] : []
    })
}

function isNode(value) {
    return value && typeof value === 'object' && typeof value.type === 'string'
}

function functionName(node, parent, line) {
    if (node.id?.name) return node.id.name
    if (parent?.id?.name) return parent.id.name
    if (parent?.key?.name) return parent.key.name
    return `anonymous@${line}`
}

function effectiveLines(content, start, end) {
    return content.slice(start, end).split(/\r?\n/u).filter(line => line.trim().length > 0).length
}

function lineAt(content, offset) {
    return content.slice(0, offset).split(/\r?\n/u).length
}
