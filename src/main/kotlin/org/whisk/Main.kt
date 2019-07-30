package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.buildlang.*
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.StringResource
import org.whisk.rule.Execution
import java.nio.file.Paths
import kotlin.reflect.full.primaryConstructor

class BuildNode(val goal: ResolvedGoal, val parents: MutableList<BuildNode> = mutableListOf(), val dependencies: MutableList<BuildNode> = mutableListOf())

fun main(vararg args: String) {
    val log = LogManager.getLogger()
    val application = DaggerApplication.create()
    val processor = application.processor()
    val graphBuilder = application.graphBuilder()

    val resolvedGoals = application.resolver().resolve(PathModuleLoader(SystemModuleLoader(), Paths.get("")), "")
    val graph = graphBuilder.buildFrom(resolvedGoals, args[0])
    println(graph)
    println(graph.nodes.filter { it.dependencies.isEmpty() })

    val pending = graph.nodes.map {
        it to BuildNode(it.goal)
    }.toMap().toMutableMap()
    graph.nodes.forEach {
        val bn = pending[it]!!
        it.dependencies.forEach { c ->
            val cn = pending[c]!!
            cn.parents += bn
            bn.dependencies += cn
        }
    }

    val executionResult = mutableMapOf<ResolvedGoal, RuleResult>()

    while (pending.isNotEmpty()) {
        val entry = pending.entries.first { it.value.dependencies.isEmpty() }
        pending.remove(entry.key)
        val nextNode = entry.value
        fun eval(value: ResolvedValue<Value>): RuleResult =
                when (value) {
                    is ResolvedRuleCall -> {
                        val nativeRule = value.rule.nativeRule
                        if (nativeRule != null) {
                            val parameters = value.params.map {
                                it.param.name to eval(it.value)
                            }.toMap()
                            val ctor = nativeRule.primaryConstructor!!
                            val kParameters = ctor.parameters.map {
                                val resources = parameters[it.name]?.resources
                                it to when {
                                    resources != null ->
                                        if (it.type.classifier == List::class) resources
                                        else {
                                            val resource = resources.single()
                                            if (it.type.classifier == FileResource::class && resource is StringResource)
                                                FileResource(Paths.get(resource.string).toAbsolutePath())
                                            else
                                                resource
                                        }
                                    it.type.classifier == List::class -> emptyList<FileResource>()
                                    else -> null
                                }
                            }.toMap()
                            val ruleParams = ctor.callBy(kParameters)
                            val runnableFuture = processor.process(Execution(nextNode.goal.name, Paths.get(".whisk"), Paths.get("."), ruleParams, Paths.get("whisk-out")))
                            log.info(nextNode.goal.name)
                            runnableFuture.run()
                            runnableFuture.get()
                        } else {
                            Failed(emptyList())
                        }
                    }
                    is ResolvedStringValue -> Success(listOf(StringResource(value.value)))
                    is ResolvedListValue -> Success(value.items.flatMap { eval(it).resources })
                    is ResolvedGoalCall -> executionResult[value.goal]!!
                    else -> throw IllegalStateException("Can't handle $value")
                }

        executionResult[nextNode.goal] = eval(nextNode.goal.value!!)
        nextNode.parents.forEach { it.dependencies -= nextNode }
    }
}