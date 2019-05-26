package org.whisk

import org.whisk.model.Rule
import org.whisk.model.TomlRuleParser
import org.whisk.rule.Processor
import org.whisk.rule.RuleResult
import java.nio.file.Paths

fun main(vararg args: String) {

    val rules = TomlRuleParser.parse(Paths.get("whisk.toml"))

    val nodes = rules.map { it.key to Node(it.value) }
        .toMap().toMutableMap()
    rules.forEach { (k, v) ->
        nodes[k]!!.children += v.deps.map {
            nodes[it.substring(1)] ?: throw IllegalArgumentException("No such node $it")
        }
    }

    while (nodes.isNotEmpty()) {
        val (name, nextNode) = nodes.entries.firstOrNull { (k, v) -> v.children.isEmpty() }
            ?: throw IllegalStateException()
        nodes -= name

//        println("node $nextNode")
        val result = Processor.process(nextNode)
//        println(cpEntries)

        nodes.values.filter {
            it.children.remove(nextNode)
        }.forEach {
            it.result += result
        }
    }


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
    fileManager.setLocation(StandardLocation.CLASS_PATH, cp)
    compiler.getTask(null, fileManager, null, null, null, files).call()
*/
}

data class Node(
    val rule: Rule,
    val children: MutableList<Node> = mutableListOf(),
    val result: MutableList<RuleResult> = mutableListOf()
)