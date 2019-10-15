package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.StopWatch
import org.whisk.buildlang.GoalDeclaration
import org.whisk.buildlang.RuleCall
import org.whisk.buildlang.SourceRef
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.model.RuleParameters
import java.nio.file.Path
import javax.inject.Inject
import kotlin.reflect.KClass

class ExecutionContext<T : RuleParameters>(
        val goalRef: SourceRef<GoalDeclaration>,
        val cacheDir: Path,
        val ruleRef: SourceRef<RuleCall>,
        val ruleParameters: T,
        val targetPath: Path
) {
    val goalName = goalRef.source.name.text
    val goalFQN = (if (goalRef.module.isEmpty()) "" else "${goalRef.module}:") + goalName
}


class RuleProcessorRegistry @Inject constructor(
        prebuiltJarHandler: PrebuiltJarHandler,
        kotlinCompileHandler: KotlinCompileHandler,
        javaCompileHandler: JavaCompileHandler,
        javaTestHandler: JavaTestHandler,
        kotlinTestHandler: KotlinTestHandler,
        remoteFileHandler: RemoteFileHandler,
        javaBinaryHandler: BuildJarHandler,
        mavenLibraryHandler: MavenLibraryHandler,
        protobufCompilerHandler: ProtobufCompilerHandler,
        globHandler: GlobHandler,
        rGlobHandler: RGlobHandler,
        antlrGenHandler: AntlrGenHandler,
        execHandler: ExecHandler,
        onWindowsHandler: OnWindowsHandler,
        onLinuxHandler: OnLinuxHandler,
        kTlintHandler: KTlintHandler
) {
    private val processors = mutableMapOf<KClass<out RuleParameters>, RuleExecutor<out RuleParameters>>()

    init {
        register(prebuiltJarHandler)
        register(kotlinCompileHandler)
        register(kotlinTestHandler)
        register(javaCompileHandler)
        register(javaTestHandler)
        register(remoteFileHandler)
        register(javaBinaryHandler)
        register(mavenLibraryHandler)
        register(protobufCompilerHandler)
        register(globHandler)
        register(rGlobHandler)
        register(antlrGenHandler)
        register(execHandler)
        register(onWindowsHandler)
        register(onLinuxHandler)
        register(kTlintHandler)
    }

    inline fun <reified T : RuleParameters> register(ruleHandler: RuleExecutor<T>) {
        val kClass = T::class
        register(kClass, ruleHandler)

    }

    fun <T : RuleParameters> register(kClass: KClass<T>, ruleHandler: RuleExecutor<T>) {
        processors[kClass] = ruleHandler
    }

    fun getRuleProcessor(paramClass: KClass<out RuleParameters>) =
            processors[paramClass] as? RuleExecutor<RuleParameters>
                    ?: throw UnsupportedRuleModel(paramClass)

    fun getRuleProcessor(model: RuleParameters) =
            processors[model::class] as? RuleExecutor<RuleParameters>
                    ?: throw UnsupportedRuleModel(model)
}

class UnsupportedRuleModel(modelClass: KClass<out RuleParameters>) :
        RuntimeException("No processor for '${modelClass::class.simpleName}' was registered! Is the handler registered and implementing the correct interface?") {
    constructor(model: RuleParameters) : this(model::class.java.kotlin)
}

class Processor @Inject constructor(private val ruleProcessorRegistry: RuleProcessorRegistry) {
    private val log = LogManager.getLogger()

    fun process(execution: ExecutionContext<RuleParameters>): RuleResult {
        val ruleProcessor = ruleProcessorRegistry.getRuleProcessor(execution.ruleParameters)
        val stopWatch = StopWatch()
        ruleProcessor.name?.let { log.info("======== Running ${execution.goalFQN}") }

        val result = ruleProcessor.execute(execution)
        ruleProcessor.name?.let {
            if (result is Failed)
                log.info("======== Failed {} in {}ms", execution.goalFQN, stopWatch.stop())
            else
                log.info("======== Completed {} in {}ms", execution.goalFQN, stopWatch.stop())
        }

        return result
    }
}
