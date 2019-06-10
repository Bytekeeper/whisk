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

    val workingSet = ArrayDeque<NodeExecutionContext>(executions.values.filter { it.remainingChildren == 0 })

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
//
//    val nodes = rules.map { it.key to Node(it.value) }
//        .toMap().toMutableMap()
//    rules.forEach { (k, v) ->
//        nodes[k]!!.children += v.deps.map {
//            nodes[it.substring(1)] ?: throw IllegalArgumentException("No such node $it")
//        }
//    }
//
//    while (nodes.isNotEmpty()) {
//        val (name, nextNode) = nodes.entries.firstOrNull { (k, v) -> v.children.isEmpty() }
//            ?: throw IllegalStateException()
//        nodes -= name
//
////        println("node $nextNode")
//        val result = Processor.process(nextNode)
////        println(cpEntries)
//
//        nodes.values.filter {
//            it.children.remove(nextNode)
//        }.forEach {
//            it.result += result
//        }
//    }
//

    /*
    val result = parse(source)
    result.errors().forEach { System.err.println(it) }

    Kotlin("whisk-out").compile()

    val cp = result.getArray("prebuilt_jar")
        ?.let { fileDependencies ->
            val res = mutableListOf<File>()
            for (i in 0 until fileDependencies.size()) {
                val table = fileDependencies.getTable(i)
                println("dependency ${table.getString("name")}")
                val file = Paths.get(table.getString("binary_jar"))
                println("file ${file.toAbsolutePath()}")
                res += file.toFile()
            }
            return@let res
        } ?: emptyList<File>()

    val compiler = ToolProvider.getSystemJavaCompiler()
    val fileManager = compiler.getStandardFileManager(null, null, null)
    val files = fileManager.getJavaFileObjects(File("test.java"))
    val whiskOut = File("whisk-out")
    whiskOut.mkdirs()
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(whiskOut))
    fileManager.setLocation(StandardLocation. CLASS_PATH, cp)
    compiler.getTask(null, fileManager, null, null, null, files).call()
*/
}