package org.whisk.execution

import org.apache.logging.log4j.LogManager
import org.whisk.BuildContext
import org.whisk.StopWatch
import org.whisk.buildlang.*
import org.whisk.forkJoinTask
import org.whisk.model.*
import org.whisk.rule.ExecutionContext
import org.whisk.rule.Processor
import java.nio.file.Paths
import java.util.concurrent.ForkJoinTask
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

class GoalExecutor constructor(private val processor: Processor) {
    private val log = LogManager.getLogger()
    private val emptyResult = forkJoinTask<RuleResult> { Success(emptyList()) }.fork()

    fun eval(buildContext: BuildContext, goalToTask: Map<ResolvedGoal, ForkJoinTask<RuleResult>>, goal: ResolvedGoal): ForkJoinTask<RuleResult> = GoalCall(goalToTask, goal).eval(buildContext)

    inner class GoalCall(private val goalTask: Map<ResolvedGoal, ForkJoinTask<RuleResult>>,
                         private val goal: ResolvedGoal) {
        fun eval(buildContext: BuildContext) = forkJoinTask {
            val stopWatch = StopWatch()
            log.debug("Processing goal {}", goal.name)
            val result = eval(buildContext, goal.value!!, emptyMap()).join()
            if (result is Failed)
                log.warn("Goal {} failed after {}ms", goal.name, stopWatch.stop())
            else
                log.debug("Goal {} ran {}ms", goal.name, stopWatch.stop())
            result
        }

        /**
         * Recursively calculate dependencies and return an already forked ForkJoinTask.
         */
        private fun eval(buildContext: BuildContext, value: ResolvedValue<Value>, passedParameters: Map<String, RuleResult>): ForkJoinTask<RuleResult> =
                when (value) {
                    is ResolvedRuleCall -> ruleCall(buildContext, value, passedParameters)
                    is ResolvedStringValue -> forkJoinTask<RuleResult> { Success(listOf(StringResource(value.value, null, value.source.module))) }.fork()
                    is ResolvedBoolValue -> forkJoinTask<RuleResult> { Success(listOf(BooleanResource(value.value, null))) }.fork()
                    is ResolvedListValue -> forkJoinTask {
                        val resultsToJoin = value.items.map { eval(buildContext, it, passedParameters) }.map { it.join() }
                        resultsToJoin.firstOrNull { it is Failed } ?: Success(resultsToJoin.flatMap { it.resources })
                    }.fork()
                    is ResolvedGoalCall -> goalTask[value.goal] ?: run {
                        log.warn("${value.goal.name} was not processed! It might be skipped which is ok but unchecked, or a graph resolving error occurred.")
                        emptyResult
                    }
                    is ResolvedRuleParamValue -> forkJoinTask {
                        passedParameters[value.parameter.name]
                                ?: error("Could not retrieve value for parameter ${value.parameter.name}")
                    }.fork()
                    else -> error("Can't handle $value")
                }

        /**
         * Call a rule, either native or defined by BL. Return as already forked ForkJoinTask.
         */
        private fun ruleCall(buildContext: BuildContext, value: ResolvedRuleCall, passedParameters: Map<String, RuleResult>): ForkJoinTask<RuleResult> {
            val childTasks = value.params.map {
                it.param.name to (passedParameters[it.param.name] ?: eval(buildContext, it.value, passedParameters))
            }
            val parameters = childTasks.map {
                it.first to ((it.second as? ForkJoinTask<RuleResult>)?.join() ?: it.second as RuleResult)
            }.toMap()
            if (parameters.values.any { it is Failed }) return forkJoinTask<RuleResult> { Failed() }.fork()

            return value.rule.nativeRule?.let { nativeRuleCall(buildContext, it, parameters, value.source) }
                    ?: eval(buildContext, value.rule.value!!, parameters)
        }

        private fun nativeRuleCall(buildContext: BuildContext, nativeRule: KClass<out RuleParameters>, parameters: Map<String, RuleResult>, source: SourceRef<RuleCall>): ForkJoinTask<RuleResult>? {
            val ctor = nativeRule.primaryConstructor!!
            val kParameters = ctor.parameters.map { param ->
                val resources = parameters[param.name]?.resources
                param to when {
                    resources != null && param.type.classifier == List::class -> {
                        val targetType = param.type.arguments.single().type!!.classifier
                        resources.map { convertResource(buildContext, it, targetType) }
                    }
                    resources != null -> convertResource(buildContext, resources.single(), param.type.classifier)
                    param.type.classifier == List::class -> emptyList<FileResource>()
                    else -> null
                }
            }.filter { it.second != null || !it.first.isOptional }
                    .toMap()
            val ruleParams = ctor.callBy(kParameters)
            return forkJoinTask {
                val realModulePath = (source.modulePath ?: Paths.get("")).toAbsolutePath()
                val targetPath = Paths.get("whisk-out")
                        .resolve(Paths.get("").toAbsolutePath().relativize(realModulePath))
                        .resolve(goal.name)
                processor.process(
                        ExecutionContext(
                                goal.source,
                                Paths.get(".whisk"),
                                source,
                                ruleParams,
                                targetPath))
            }.fork()
        }

        private fun convertResource(buildContext: BuildContext, resource: Resource, expectedResource: KClassifier?) =
                when {
                    resource.javaClass.kotlin.isSubclassOf(expectedResource as KClass<*>) -> resource
                    expectedResource == FileResource::class && resource is StringResource -> {
                        val modulePath = buildContext.projectPath
                                .resolve(resource.definingModule.toModulePath())
                                .toAbsolutePath()
                        val filePath = modulePath
                                .resolve(resource.string)
                        FileResource(
                                filePath,
                                if (filePath.startsWith(modulePath)) modulePath else filePath.root,
                                resource.source)
                    }
                    else -> error("Cannot convert $resource to $expectedResource")
                }
    }
}