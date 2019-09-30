package org.whisk

import org.whisk.buildlang.PathModuleLoader
import org.whisk.buildlang.ResolvedGoal
import org.whisk.buildlang.SystemModuleLoader
import org.whisk.execution.Failed
import org.whisk.execution.GoalExecutor
import org.whisk.execution.RuleResult
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask
import kotlin.system.exitProcess

fun <T> forkJoinTask(producer: () -> T) = ForkJoinTask.adapt(Callable { producer() })

class BuildNode(val goal: ResolvedGoal, val parents: MutableList<BuildNode> = mutableListOf(), val dependencies: MutableList<BuildNode> = mutableListOf())

fun main(vararg args: String) {
    val application = DaggerApplication.create()
    val processor = application.processor()
    val graphBuilder = application.graphBuilder()

    val resolvedGoals = application.resolver().resolve(PathModuleLoader(SystemModuleLoader(), Paths.get("")), "")
    val graph = graphBuilder.buildFrom(resolvedGoals, args[0])

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

    val ruleEval = GoalExecutor(processor)

    val goalToTask = mutableMapOf<ResolvedGoal, ForkJoinTask<RuleResult>>()
    val tasks = mutableListOf<ForkJoinTask<RuleResult>>()


    while (pending.isNotEmpty()) {
        pending.entries.filter {
            val dependencies = it.value.dependencies
            dependencies.isEmpty()
        }.forEach {
            pending.remove(it.key)
            val node = it.value
            node.parents.forEach { parent ->
                parent.dependencies -= node
            }
            val task = ruleEval.eval(goalToTask, node.goal)
            tasks += task
            goalToTask[node.goal] = task
        }
    }
    tasks.forEach { it.fork() }
    tasks.forEach { it.join() }
    if (goalToTask.values.any { it.get() is Failed }) exitProcess(1)
}