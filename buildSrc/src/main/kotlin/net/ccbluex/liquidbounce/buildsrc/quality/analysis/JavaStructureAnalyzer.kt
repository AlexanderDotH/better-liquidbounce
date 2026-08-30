/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.buildsrc.quality.analysis

import com.sun.source.tree.BinaryTree
import com.sun.source.tree.CatchTree
import com.sun.source.tree.CompilationUnitTree
import com.sun.source.tree.ConditionalExpressionTree
import com.sun.source.tree.DoWhileLoopTree
import com.sun.source.tree.EnhancedForLoopTree
import com.sun.source.tree.ForLoopTree
import com.sun.source.tree.IfTree
import com.sun.source.tree.LambdaExpressionTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.SwitchExpressionTree
import com.sun.source.tree.SwitchTree
import com.sun.source.tree.Tree
import com.sun.source.tree.TryTree
import com.sun.source.tree.WhileLoopTree
import com.sun.source.util.JavacTask
import com.sun.source.util.SourcePositions
import com.sun.source.util.TreeScanner
import com.sun.source.util.Trees
import net.ccbluex.liquidbounce.buildsrc.quality.model.Finding
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceFile
import net.ccbluex.liquidbounce.buildsrc.quality.model.SourceKind
import net.ccbluex.liquidbounce.buildsrc.quality.model.findingOrder
import java.net.URI
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider

class JavaStructureAnalyzer(private val limits: StructuralLimitPolicy) {
    fun analyze(files: Collection<SourceFile>): List<Finding> = files
        .filter { it.normalizedPath.endsWith(".java") }
        .flatMap(::analyzeFile)
        .sortedWith(findingOrder)

    private fun analyzeFile(file: SourceFile): List<Finding> {
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Java 25 compiler is required" }
        val source = InMemoryJavaSource(file)
        val task = compiler.getTask(null, null, null, listOf("-proc:none"), null, listOf(source)) as JavacTask
        val units = task.parse().toList()
        val positions = Trees.instance(task).sourcePositions
        return units.flatMap { unit ->
            val methods = mutableListOf<MethodTree>()
            val lambdas = mutableListOf<LambdaExpressionTree>()
            object : TreeScanner<Unit, Unit>() {
                override fun visitMethod(node: MethodTree, unused: Unit?) {
                    if (node.body != null) methods += node
                    super.visitMethod(node, unused)
                }

                override fun visitLambdaExpression(node: LambdaExpressionTree, unused: Unit?) {
                    lambdas += node
                    super.visitLambdaExpression(node, unused)
                }
            }.scan(unit, Unit)
            val methodFindings = methods.flatMap { method ->
                executableFindings(
                    file,
                    method.name.toString().replace("<init>", "constructor"),
                    method.body,
                    method,
                    unit,
                    positions,
                )
            }
            val lambdaFindings = lambdas.flatMap { lambda ->
                val start = positions.getStartPosition(unit, lambda).toInt()
                val line = unit.lineMap.getLineNumber(start.toLong()).toInt()
                executableFindings(file, "lambda@$line", lambda.body, lambda, unit, positions)
            }
            methodFindings + lambdaFindings
        }
    }

    private fun executableFindings(
        file: SourceFile,
        name: String,
        body: Tree,
        executable: Tree,
        unit: CompilationUnitTree,
        positions: SourcePositions,
    ): List<Finding> {
        val start = positions.getStartPosition(unit, executable).toInt()
        val end = positions.getEndPosition(unit, executable).toInt()
        val line = unit.lineMap.getLineNumber(start.toLong()).toInt()
        return findings(file, name, body, start, end, line)
    }

    private fun findings(file: SourceFile, name: String, body: Tree, start: Int, end: Int, line: Int): List<Finding> = buildList {
        if (start < 0 || end <= start || end > file.content.length) return@buildList
        val methodLimit = if (file.kind == SourceKind.TEST) limits.testMethodLines else limits.productionMethodLines
        val methodLines = file.content.substring(start, end).lineSequence().count { it.isNotBlank() }
        if (methodLines > methodLimit) add(finding(file, line, name, "method-lines", methodLines, methodLimit))
        if (file.kind == SourceKind.TEST) return@buildList
        val metrics = JavaMethodMetrics.measure(body)
        if (metrics.complexity > limits.cognitiveComplexity) {
            add(finding(file, line, name, "cognitive-complexity", metrics.complexity, limits.cognitiveComplexity))
        }
        if (metrics.nesting > limits.nestingDepth) {
            add(finding(file, line, name, "nesting-depth", metrics.nesting, limits.nestingDepth))
        }
    }

    private fun finding(file: SourceFile, line: Int, name: String, metric: String, measured: Int, limit: Int) = Finding(
        ruleId = "LB-HYG-002",
        path = file.normalizedPath,
        line = line,
        subject = "$metric:$name",
        message = "Java method $name has $measured $metric units; the limit is $limit.",
        recommendation = "Extract one decision or responsibility from $name into a named collaborator.",
        documentation = ".github/CODING_STANDARDS.md#lb-hyg-002",
        measuredValue = measured,
        limit = limit,
    )
}

private class InMemoryJavaSource(private val source: SourceFile) :
    SimpleJavaFileObject(URI.create("string:///${source.normalizedPath.replace(' ', '_')}"), JavaFileObject.Kind.SOURCE) {
    override fun getCharContent(ignoreEncodingErrors: Boolean) = source.content
}

private data class JavaMetrics(val complexity: Int, val nesting: Int)

private object JavaMethodMetrics {
    fun measure(body: Tree): JavaMetrics {
        val scanner = MetricScanner()
        scanner.scan(body, 0)
        return JavaMetrics(scanner.complexity, scanner.maximumNesting)
    }

    private class MetricScanner : TreeScanner<Unit, Int>() {
        var complexity = 0
        var maximumNesting = 0

        override fun visitIf(node: IfTree, depth: Int) = nested(depth) {
            complexity += 1 + depth
            scan(node.condition, depth)
            scan(node.thenStatement, depth + 1)
            node.elseStatement?.let { alternative ->
                scan(alternative, if (alternative is IfTree) depth else depth + 1)
            }
        }
        override fun visitSwitch(node: SwitchTree, depth: Int) = decision(node, depth) { super.visitSwitch(node, depth + 1) }
        override fun visitSwitchExpression(node: SwitchExpressionTree, depth: Int) =
            decision(node, depth) { super.visitSwitchExpression(node, depth + 1) }
        override fun visitForLoop(node: ForLoopTree, depth: Int) = decision(node, depth) { super.visitForLoop(node, depth + 1) }
        override fun visitEnhancedForLoop(node: EnhancedForLoopTree, depth: Int) =
            decision(node, depth) { super.visitEnhancedForLoop(node, depth + 1) }
        override fun visitWhileLoop(node: WhileLoopTree, depth: Int) = decision(node, depth) { super.visitWhileLoop(node, depth + 1) }
        override fun visitDoWhileLoop(node: DoWhileLoopTree, depth: Int) =
            decision(node, depth) { super.visitDoWhileLoop(node, depth + 1) }
        override fun visitCatch(node: CatchTree, depth: Int) = decision(node, depth) { super.visitCatch(node, depth + 1) }
        override fun visitConditionalExpression(node: ConditionalExpressionTree, depth: Int) =
            decision(node, depth) { super.visitConditionalExpression(node, depth + 1) }
        override fun visitTry(node: TryTree, depth: Int) = nested(depth) { super.visitTry(node, depth + 1) }
        override fun visitLambdaExpression(node: LambdaExpressionTree, depth: Int) = Unit

        override fun visitBinary(node: BinaryTree, depth: Int) {
            if (node.kind == Tree.Kind.CONDITIONAL_AND || node.kind == Tree.Kind.CONDITIONAL_OR) complexity++
            super.visitBinary(node, depth)
        }

        private fun decision(node: Tree, depth: Int, scan: () -> Unit) = nested(depth) {
            complexity += 1 + depth
            scan()
        }

        private fun nested(depth: Int, scan: () -> Unit) {
            maximumNesting = maxOf(maximumNesting, depth + 1)
            scan()
        }
    }
}
