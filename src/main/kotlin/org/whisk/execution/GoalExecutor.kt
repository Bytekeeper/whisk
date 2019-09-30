package org.whisk.execution

import org.apache.logging.log4j.LogManager
import org.whisk.StopWatch
import org.whisk.buildlang.*
import org.whisk.forkJoinTask
import org.whisk.model.FileResource
import org.whisk.model.Resource
import org.whisk.model.RuleParameters
import org.whisk.model.StringResource
import org.whisk.rule.ExecutionContext
import org.whisk.rule.Processor
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ForkJoinTask
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

class GoalExecutor constructor(private val processor: Processor) {
    private val log = LogManager.getLogger()

    fun eval(goalToTask: Map<ResolvedGoal, ForkJoinTask<RuleResult>>, goal: ResolvedGoal): ForkJoinTask<RuleResult> = GoalCall(goalToTask, goal).eval()

    inner class GoalCall(private val goalTask: Map<ResolvedGoal, ForkJoinTask<RuleResult>>,
                         private val goal: ResolvedGoal) {
        fun eval() = forkJoinTask {
            val stopWatch = StopWatch()
            log.debug("Processing goal {}", goal.name)
            val result = eval(goal.value!!, emptyMap()).join()
            if (result is Failed)
                log.warn("Goal {} failed after {}ms", goal.name, stopWatch.stop())
            else
                log.debug("Goal {} ran {}ms", goal.name, stopWatch.stop())
            result
        }

        /**
         * Recursively calculate dependencies and return an already forked ForkJoinTask.
         */
        private fun eval(value: ResolvedValue<Value>, passedParameters: Map<String, RuleResult>): ForkJoinTask<RuleResult> =
                when (value) {
                    is ResolvedRuleCall -> ruleCall(value, passedParameters)
                    is ResolvedStringValue -> forkJoinTask<RuleResult> { Success(listOf(StringResource(value.value, null))) }.fork()
                    is ResolvedListValue -> forkJoinTask {
                        val resultsToJoin = value.items.map { eval(it, passedParameters) }.map { it.join() }
                        resultsToJoin.firstOrNull { it is Failed } ?: Success(resultsToJoin.flatMap { it.resources })
                    }.fork()
                    is ResolvedGoalCall -> goalTask[value.goal]
                            ?: error("Goal ${value.goal.name} should have been processed, but was not (yet)")
                    is ResolvedRuleParamValue -> forkJoinTask {
                        passedParameters[value.parameter.name]
                                ?: error("Could not retrieve value for parameter ${value.parameter.name}")
                    }.fork()
                    else -> error("Can't handle $value")
                }

        /**
         * Call a rule, either native or defined by BL. Return as already forked ForkJoinTask.
         */
        private fun ruleCall(value: ResolvedRuleCall, passedParameters: Map<String, RuleResult>): ForkJoinTask<RuleResult> {
            val childTasks = value.params.map {
                it.param.name to (passedParameters[it.param.name] ?: eval(it.value, passedParameters))
            }
            val parameters = childTasks.map {
                it.first to ((it.second as? ForkJoinTask<RuleResult>)?.join() ?: it.second as RuleResult)
            }.toMap()
            if (parameters.values.any { it is Failed }) return forkJoinTask<RuleResult> { Failed() }.fork()

            return value.rule.nativeRule?.let { nativeRuleCall(it, parameters, value.source.modulePath) }
                    ?: eval(value.rule.value!!, parameters)
        }

        private fun nativeRuleCall(nativeRule: KClass<out RuleParameters>, parameters: Map<String, RuleResult>, modulePath: Path?): ForkJoinTask<RuleResult>? {
            val ctor = nativeRule.primaryConstructor!!
            val kParameters = ctor.parameters.map { param ->
                val resources = parameters[param.name]?.resources
                param to when {
                    resources != null && param.type.classifier == List::class -> {
                        val targetType = param.type.arguments.single().type!!.classifier
                        resources.map { convertResource(it, targetType) }
                    }
                    resources != null -> convertResource(resources.single(), param.type.classifier)
                    param.type.classifier == List::class -> emptyList<FileResource>()
                    else -> null
                }
            }.filter { it.second != null || !it.first.isOptional }
                    .toMap()
            val ruleParams = ctor.callBy(kParameters)
            return forkJoinTask {
                val realModulePath = (modulePath ?: Paths.get("")).toAbsolutePath()
                processor.process(
                        ExecutionContext(
                                goal.name,
                                Paths.get(".whisk"),
                                realModulePath,
                                ruleParams,
                                Paths.get("whisk-out").resolve(Paths.get("").toAbsolutePath().relativize(realModulePath))))
            }.fork()
        }

        private fun convertResource(resource: Resource, expectedResource: KClassifier?) =
                when {
                    resource.javaClass.kotlin.isSubclassOf(expectedResource as KClass<*>) -> resource
                    expectedResource == FileResource::class && resource is StringResource ->
                        FileResource(Paths.get(resource.string).toAbsolutePath(), source = resource.source)
                    else -> error("Cannot convert $resource to $expectedResource")
                }
    }
}