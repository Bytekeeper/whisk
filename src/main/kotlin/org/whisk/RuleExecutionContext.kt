package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.buildlang.*
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.StringResource
import org.whisk.rule.Execution
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
            log.info(goal.name)
            eval(goal.value!!).join()
        }

        private fun eval(value: ResolvedValue<Value>): ForkJoinTask<RuleResult> {
            val task: ForkJoinTask<RuleResult> = when (value) {
                is ResolvedRuleCall -> ruleCall(value).fork()
                is ResolvedStringValue -> forkJoinTask<RuleResult> { Success(listOf(StringResource(value.value, null))) }.fork()
                is ResolvedListValue -> forkJoinTask<RuleResult> { Success(value.items.flatMap { eval(it).join().resources }) }.fork()
                is ResolvedGoalCall -> goalTask[value.goal]!!
                else -> throw IllegalStateException("Can't handle $value")
            }
            return task
        }

        private fun ruleCall(value: ResolvedRuleCall): ForkJoinTask<RuleResult> {
            val nativeRule = value.rule.nativeRule
            if (nativeRule != null) {
                val childTasks = value.params.map {
                    it.param.name to eval(it.value)
                }
                val parameters = childTasks.map {
                    it.first to it.second.join()
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
                                    FileResource(Paths.get(resource.string).toAbsolutePath(), source = resource.source)
                                else
                                    resource
                            }
                        it.type.classifier == List::class -> emptyList<FileResource>()
                        else -> null
                    }
                }.toMap()
                val ruleParams = ctor.callBy(kParameters)
                return forkJoinTask { processor.process(Execution(goal.name, Paths.get(".whisk"), Paths.get("."), ruleParams, Paths.get("whisk-out"))) }
            } else {
                return forkJoinTask { Failed(emptyList()) }
            }
        }
    }
}