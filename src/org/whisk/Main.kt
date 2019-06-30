package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.rule.Execution
import org.whisk.rule.RuleInput
import org.whisk.rule.RuleResult
import java.nio.file.Paths
import java.util.*

class NodeExecutionContext(
    val node: Node,
    val input: MutableMap<Any, MutableList<RuleResult>> = mutableMapOf(),
    var remainingChildren: Int = node.children.map { it.value.size }.sum(),
    val parents: MutableMap<Any, MutableList<NodeExecutionContext>> = mutableMapOf(),
    var executable: Boolean = false
)

fun main(vararg args: String) {
    val log = LogManager.getLogger()
    val application = DaggerApplication.create()
    val processor = application.processor()
    val graph = application.graph()

    val projectRoot = Paths.get(".")
    val cacheDir = Paths.get(".whisk")
    val targetPath = Paths.get("whisk-out")
    val buildGraph = graph.load(projectRoot)
    val executions = buildGraph.values.map { it to NodeExecutionContext(it) }.toMap()
    executions.forEach { node, parent ->
        node.children.forEach { (group, children) ->
            children.forEach { child ->
                executions[child]!!.parents.computeIfAbsent(group) { mutableListOf() } += parent
            }
        }
    }
    val taskList = mutableSetOf<NodeExecutionContext>()
    val openList = ArrayDeque(executions.filter { (k, v) ->  k.rule.toString() == args[0]}.map { it.value })
    while (openList.isNotEmpty()) {
        val executionContext = openList.pop()
        if (!taskList.contains(executionContext)) {
            taskList += executionContext
            openList += executionContext.node.children.values.flatten().map { executions[it] }
        }
    }

    val workingSet = ArrayDeque(taskList.filter { it.remainingChildren == 0 })

    while (workingSet.isNotEmpty()) {
        val next = workingSet.pop()

//        println(next.fromRule.name + " / " + workingSet.map { it.fromRule.name })
        val childNode = next.node
        val ruleResult = processor.process(
            Execution(
                cacheDir,
                projectRoot.resolve(childNode.rule.module.substring(1)),
                childNode.ruleModel,
                RuleInput(next.input),
                targetPath.resolve(childNode.rule.module.substring(1))
            )
        )

        next.parents.forEach { (group, parents) ->
            parents.forEach { parent ->
                parent.remainingChildren--
                if (parent.remainingChildren < 0) throw IllegalStateException()
                parent.input.computeIfAbsent(group) { mutableListOf() } += ruleResult
                if (!parent.executable && parent.remainingChildren == 0) {
                    workingSet += parent
                    parent.executable = true
                }
            }
        }
    }
    println("DONE")
    /*
*/
}