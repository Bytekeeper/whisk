package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.model.RuleParameters
import java.nio.file.Path
import java.util.concurrent.RunnableFuture
import javax.inject.Inject
import kotlin.reflect.KClass

class Execution<T : RuleParameters>(
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
        javaBinaryHandler: JavaBinaryHandler,
        mavenLibraryHandler: MavenLibraryHandler,
        protobufCompilerHandler: ProtobufCompilerHandler,
        globHandler: GlobHandler
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
    }

    inline fun <reified T : RuleParameters> register(ruleHandler: RuleExecutor<T>) {
        val kClass = T::class
        register(kClass, ruleHandler)

    }

    fun <T : RuleParameters> register(kClass: KClass<T>, ruleHandler: RuleExecutor<T>) {
        processors.put(kClass, ruleHandler)
    }

    fun getRuleProcessor(model: RuleParameters) =
            processors[model::class] as? RuleExecutor<RuleParameters>
                    ?: throw UnsupportedRuleModel(model)
}

class UnsupportedRuleModel(model: RuleParameters) :
        RuntimeException("No processor for '${model::class.simpleName}' was registered! Is the handler registered and implementing the correct interface?")

class Processor @Inject constructor(private val ruleProcessorRegistry: RuleProcessorRegistry) {
    fun process(execution: Execution<RuleParameters>): RunnableFuture<RuleResult> =
            ruleProcessorRegistry.getRuleProcessor(execution.ruleParameters).execute(execution)
}
