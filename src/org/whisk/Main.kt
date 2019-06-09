package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.model.RuleModel
import org.whisk.rule.Execution
import org.whisk.rule.RuleInput
import org.whisk.rule.RuleResult
import java.nio.file.Paths
import java.util.*

class TaskNode(
    val rule: RuleModel,
    val children: MutableList<TaskNode> = mutableListOf(),
    val parents: MutableMap<Any, MutableList<TaskNode>> = mutableMapOf(),
    val input: MutableMap<Any, MutableList<RuleResult>> = mutableMapOf(),
    var executable : Boolean = false
)

fun main(vararg args: String) {
    val log = LogManager.getLogger()
    val application = DaggerApplication.create()
    val ruleParser = application.ruleParser()
    val processor = application.processor()

    val rules = ruleParser.parse(Paths.get("WHISK.toml"))
    val nodes = rules.map { it.key to TaskNode(it.value) }.toMap()
    val workingSet = ArrayDeque<TaskNode>()
    nodes.values.forEach { n ->
        val dependencyReferences = processor.retrieveDependencyReferences(n.rule)
        dependencyReferences.refs.forEach { group, deps ->
            n.children += deps.map {
                nodes[it.substring(1)]
                    ?: throw IllegalStateException("Rule '${n.rule.name}' depends on non-existent fromRule '$it'")
            }
            n.children.forEach { child ->
                child.parents.computeIfAbsent(group) { mutableListOf() } += n
            }
        }
        if (n.children.isEmpty()) workingSet += n
    }


    while (workingSet.isNotEmpty()) {
        val next = workingSet.pop()

//        println(next.fromRule.name + " / " + workingSet.map { it.fromRule.name })
        val ruleResult = processor.process(Execution(next.rule, RuleInput(next.input)))

        next.parents.forEach { (group, parents) ->
            parents.forEach { parent ->
                parent.children -= next
                parent.input.computeIfAbsent(group) { mutableListOf() } += ruleResult
                if (!parent.executable && parent.children.isEmpty()) {
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