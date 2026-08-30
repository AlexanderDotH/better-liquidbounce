const FUNCTION_KINDS = new Set([
    'ArrowFunction',
    'Constructor',
    'FunctionDeclaration',
    'FunctionExpression',
    'GetAccessor',
    'MethodDeclaration',
    'SetAccessor',
])

export function analyzeTypeScriptFunctions(ts, file) {
    const kind = scriptKind(ts, file.path)
    const source = ts.createSourceFile(file.path, file.content, ts.ScriptTarget.Latest, true, kind)
    const results = []
    visit(source, node => {
        if (!isFunction(ts, node) || !node.body) return
        const start = node.getStart(source)
        const end = node.end
        const line = source.getLineAndCharacterOfPosition(start).line + 1
        const name = functionName(node, source, line)
        const metrics = measure(ts, node.body)
        results.push(...metricRows(file, name, line, effectiveLines(file.content, start, end), metrics))
    }, ts)
    return results
}

function visit(node, consumer, ts) {
    consumer(node)
    ts.forEachChild(node, child => visit(child, consumer, ts))
}

function measure(ts, root) {
    let complexity = 0
    let nesting = 0
    scan(root, 0)
    return { complexity, nesting }

    function scan(node, depth) {
        if (node !== root && isFunction(ts, node)) return
        if (ts.isIfStatement(node)) {
            complexity += 1 + depth
            nesting = Math.max(nesting, depth + 1)
            scan(node.expression, depth)
            scan(node.thenStatement, depth + 1)
            if (node.elseStatement) scan(node.elseStatement, ts.isIfStatement(node.elseStatement) ? depth : depth + 1)
            return
        }
        if (isDecision(ts, node)) {
            complexity += 1 + depth
            nesting = Math.max(nesting, depth + 1)
            ts.forEachChild(node, child => scan(child, depth + 1))
            return
        }
        if (ts.isTryStatement(node)) {
            nesting = Math.max(nesting, depth + 1)
            ts.forEachChild(node, child => scan(child, depth + 1))
            return
        }
        if (ts.isBinaryExpression(node) && isLogical(ts, node.operatorToken.kind)) complexity++
        ts.forEachChild(node, child => scan(child, depth))
    }
}

function isFunction(ts, node) {
    return FUNCTION_KINDS.has(ts.SyntaxKind[node.kind])
}

function isDecision(ts, node) {
    return ts.isSwitchStatement(node) || ts.isForStatement(node) ||
        ts.isForInStatement(node) || ts.isForOfStatement(node) || ts.isWhileStatement(node) ||
        ts.isDoStatement(node) || ts.isCatchClause(node) || ts.isConditionalExpression(node)
}

function isLogical(ts, kind) {
    return kind === ts.SyntaxKind.AmpersandAmpersandToken || kind === ts.SyntaxKind.BarBarToken ||
        kind === ts.SyntaxKind.QuestionQuestionToken
}

function functionName(node, source, line) {
    if (node.name) return node.name.getText(source)
    if (node.parent?.name) return node.parent.name.getText(source)
    return `anonymous@${line}`
}

function effectiveLines(content, start, end) {
    return content.slice(start, end).split(/\r?\n/u).filter(line => line.trim().length > 0).length
}

function metricRows(file, name, line, methodLines, metrics) {
    return [
        { path: file.path, name, line, metric: 'method-lines', measured: methodLines },
        { path: file.path, name, line, metric: 'cognitive-complexity', measured: metrics.complexity },
        { path: file.path, name, line, metric: 'nesting-depth', measured: metrics.nesting },
    ]
}

function scriptKind(ts, path) {
    if (path.endsWith('.ts')) return ts.ScriptKind.TS
    if (path.endsWith('.tsx')) return ts.ScriptKind.TSX
    if (path.endsWith('.jsx')) return ts.ScriptKind.JSX
    return ts.ScriptKind.JS
}
