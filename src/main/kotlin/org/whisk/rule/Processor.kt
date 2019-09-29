package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.StopWatch
import org.whisk.execution.RuleResult
import org.whisk.model.RuleParameters
import java.nio.file.Path
import javax.inject.Inject
import kotlin.reflect.KClass

class ExecutionContext<T : RuleParameters>(
        val goalName: String,
        val cacheDir: Path,
        val modulePath: Path,
        val ruleParameters: T,
        val targetPath: Path
)


class RuleProcessorRegistry @Inject constructor(
        prebuiltJarHandler: PrebuiltJarHandler,
        kotlinCompileHandler: KotlinCompileHandler,
        javaCompileHandler: JavaCompileHandler,
        kotlinTestHandler: KotlinTestHandler,
        remoteFileHandler: RemoteFileHandler,
        javaBinaryHandler: BuildJarHandler,
        mavenLibraryHandler: MavenLibraryHandler,
        protobufCompilerHandler: ProtobufCompilerHandler,
        globHandler: GlobHandler,
        rGlobHandler: RGlobHandler,
        antlrGenHandler: AntlrGenHandler,
        execHandler: ExecHandler
) {
    private val processors = mutableMapOf<KClass<out RuleParameters>, RuleExecutor<out RuleParameters>>()

    init {
        register(prebuiltJarHandler)
        register(kotlinCompileHandler)
        register(kotlinTestHandler)
        register(javaCompileHandler)
        register(remoteFileHandler)
        register(javaBinaryHandler)
        register(mavenLibraryHandler)
        register(protobufCompilerHandler)
        register(globHandler)
        register(rGlobHandler)
        register(antlrGenHandler)
        register(execHandler)
    }

    inline fun <reified T : RuleParameters> register(ruleHandler: RuleExecutor<T>) {
        val kClass = T::class
        register(kClass, ruleHandler)

    }

    fun <T : RuleParameters> register(kClass: KClass<T>, ruleHandler: RuleExecutor<T>) {
        processors[kClass] = ruleHandler
    }

    fun getRuleProcessor(model: RuleParameters) =
            processors[model::class] as? RuleExecutor<RuleParameters>
                    ?: throw UnsupportedRuleModel(model)
}

class UnsupportedRuleModel(model: RuleParameters) :
        RuntimeException("No processor for '${model::class.simpleName}' was registered! Is the handler registered and implementing the correct interface?")

class Processor @Inject constructor(private val ruleProcessorRegistry: RuleProcessorRegistry) {
    private val log = LogManager.getLogger()

    fun process(execution: ExecutionContext<RuleParameters>): RuleResult {
        val ruleProcessor = ruleProcessorRegistry.getRuleProcessor(execution.ruleParameters)
        val stopWatch = StopWatch()
        ruleProcessor.name?.let { log.info("======== Running $it") }

        val result = ruleProcessor.execute(execution)
        ruleProcessor.name?.let { log.info("======== Completed {} in {}ms", it, stopWatch.stop()) }

        return result
    }
}
