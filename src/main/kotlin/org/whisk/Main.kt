package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.buildlang.PathModuleLoader
import org.whisk.buildlang.ResolvedGoal
import org.whisk.buildlang.SystemModuleLoader
import org.whisk.execution.Failed
import org.whisk.execution.GoalExecutor
import org.whisk.execution.RuleResult
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import kotlin.math.max
import kotlin.system.exitProcess

fun <T> forkJoinTask(producer: () -> T) = ForkJoinTask.adapt(Callable { producer() })

class BuildNode(val goal: ResolvedGoal, val parents: MutableList<BuildNode> = mutableListOf(), val dependencies: MutableList<BuildNode> = mutableListOf())

fun main(vararg args: String) {
    LogManager.getContext()
    val application = DaggerApplication.create()
    val processor = application.processor()
    val graphBuilder = application.graphBuilder()

    val resolvedGoals = application.resolver().resolve(PathModuleLoader(SystemModuleLoader(), Paths.get("")), "_")
    val graph = graphBuilder.buildFrom(resolvedGoals, args[0])

    Authenticator.setDefault(object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val buildProperties = application.buildProperties()
            if (buildProperties.username(requestingHost) == null) return null
            if (buildProperties.password(requestingHost) == null) return null
            return PasswordAuthentication(buildProperties.username(requestingHost), buildProperties.password(requestingHost)?.toCharArray())
        }
    })


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
    val buildContext = BuildContext(
            projectPath = Paths.get("")
    )


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
            val task = ruleEval.eval(buildContext, goalToTask, node.goal)
            tasks += task
            goalToTask[node.goal] = task
        }
    }
    val fjp = ForkJoinPool(max(Runtime.getRuntime().availableProcessors() * 3 / 2, 4))
    tasks.forEach { fjp.execute(it) }
//    while (tasks.any { !it.isDone }) {
//        println("Main: Parallelism: ${fjp.parallelism}")
//        println("Main: Active Threads: ${fjp.activeThreadCount}")
//        println("Main: Task Count: ${fjp.queuedTaskCount}")
//        println("Main: Steal Count: ${fjp.stealCount}")
//        TimeUnit.SECONDS.sleep(1)
//    }
    tasks.forEach { it.quietlyJoin() }

    application.cleaner().run()
    if (goalToTask.values.any { it.get() is Failed }) exitProcess(1)
}