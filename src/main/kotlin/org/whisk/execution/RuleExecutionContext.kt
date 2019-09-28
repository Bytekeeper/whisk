package org.whisk.execution

import org.apache.logging.log4j.LogManager
import org.whisk.StopWatch
import org.whisk.buildlang.*
import org.whisk.forkJoinTask
import org.whisk.model.FileResource
import org.whisk.model.StringResource
import org.whisk.rule.ExecutionContext
import org.whisk.rule.Processor
import java.nio.file.Paths
import java.util.concurrent.ForkJoinTask
import kotlin.reflect.full.primaryConstructor

class RuleExecutionContext constructor(private val processor: Processor) {
    private val log = LogManager.getLogger()

    fun eval(goalToTask: Map<ResolvedGoal, ForkJoinTask<RuleResult>>, goal: ResolvedGoal): ForkJoinTask<RuleResult> = GoalCall(goalToTask, goal).eval()

    inner class GoalCall(private val goalTask: Map<ResolvedGoal, ForkJoinTask<RuleResult>>,
                         private val goal: ResolvedGoal) {
        fun eval() = forkJoinTask {
            val stopWatch = StopWatch()
            log.info("Processing goal {}", goal.name)
            val result = eval(goal.value!!, emptyMap()).join()
            if (result is Failed)
                log.info("Goal {} failed after {}ms", goal.name, stopWatch.stop())
            else
                log.info("Goal {} ran {}ms", goal.name, stopWatch.stop())
            result
        }

        /**
         * Recursively calculate dependencies and return an already forked ForkJoinTask.
         */
        private fun eval(value: ResolvedValue<Value>, predetermined: Map<String, RuleResult>): ForkJoinTask<RuleResult> =
                when (value) {
                    is ResolvedRuleCall -> ruleCall(value, predetermined)
                    is ResolvedStringValue -> forkJoinTask<RuleResult> { Success(listOf(StringResource(value.value, null))) }.fork()
                    is ResolvedListValue -> forkJoinTask<RuleResult> {
                        val resultsToJoin = value.items.map { eval(it, predetermined) }.map { it.join() }
                        resultsToJoin.firstOrNull { it is Failed } ?: Success(resultsToJoin.flatMap { it.resources })
                    }.fork()
                    is ResolvedGoalCall -> goalTask[value.goal]
                            ?: error("Goal ${value.goal.name} should have been processed, but was not (yet).")
                    is ResolvedRuleParamValue -> forkJoinTask {
                        predetermined[value.parameter.name]!!
                    }.fork()
                    else -> throw IllegalStateException("Can't handle $value")
                }

        /**
         * Call a rule, either native or defined by BL. Return as already forked ForkJoinTask.
         */
        private fun ruleCall(value: ResolvedRuleCall, predetermined: Map<String, RuleResult>): ForkJoinTask<RuleResult> {
            val childTasks = value.params.map {
                it.param.name to (predetermined[it.param.name] ?: eval(it.value, predetermined))
            }
            val parameters = childTasks.map {
                it.first to ((it.second as? ForkJoinTask<RuleResult>)?.join() ?: it.second as RuleResult)
            }.toMap()
            if (parameters.values.any { it is Failed }) return forkJoinTask { Failed() }

            val nativeRule = value.rule.nativeRule
            if (nativeRule != null) {
                val ctor = nativeRule.primaryConstructor!!
                val kParameters = ctor.parameters.map {
                    val resources = parameters[it.name]?.resources
                    it to when {
                        resources != null ->
                            if (it.type.classifier == List::class) resources
                            else {
                                val resource = resources.single()
                                if (it.type.classifier == FileResource::class && resource is StringResource)
                                    FileResource(Paths.get(resource.string).toAbsolutePath(), source = resource.source)
                                else
                                    resource
                            }
                        it.type.classifier == List::class -> emptyList<FileResource>()
                        else -> null
                    }
                }.toMap()
                val ruleParams = ctor.callBy(kParameters)
                return forkJoinTask { processor.process(ExecutionContext(goal.name, Paths.get(".whisk"), Paths.get("."), ruleParams, Paths.get("whisk-out"))) }
                        .fork()
            } else {
                return eval(value.rule.value!!, parameters)
            }
        }
    }
}