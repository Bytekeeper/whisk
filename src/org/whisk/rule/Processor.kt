package org.whisk.rule

import org.whisk.model.RuleModel
import java.nio.file.Path
import javax.inject.Inject
import kotlin.reflect.KClass

data class RuleInput(val results: Map<Any, List<RuleResult>>) {
    fun allResults() = results.values.flatten()
}

class Execution<T : RuleModel>(
    val cacheDir: Path,
    val modulePath: Path,
    val rule: T,
    val ruleInput: RuleInput,
    val targetPath: Path
)


class RuleProcessorRegistry @Inject constructor(
    prebuiltJarHandler: PrebuiltJarHandler,
    kotlinCompileHandler: KotlinCompileHandler,
    kotlinTestHandler: KotlinTestHandler,
    remoteFileHandler: RemoteFileHandler,
    javaBinaryHandler: JavaBinaryHandler,
    mavenLibraryHandler: MavenLibraryHandler
) {
    private val processors = mutableMapOf<KClass<out RuleModel>, RuleHandler<out RuleModel>>()

    init {
        register(prebuiltJarHandler)
        register(kotlinCompileHandler)
        register(kotlinTestHandler)
        register(remoteFileHandler)
        register(javaBinaryHandler)
        register(mavenLibraryHandler)
    }

    inline fun <reified T : RuleModel> register(ruleHandler: RuleHandler<T>) {
        val kClass = T::class
        register(kClass, ruleHandler)

    }

    fun <T : RuleModel> register(kClass: KClass<T>, ruleHandler: RuleHandler<T>) {
        processors.put(kClass, ruleHandler)
    }

    fun getRuleProcessor(model: RuleModel) =
        processors[model::class] as RuleHandler<RuleModel> ?: throw UnsupportedRuleModel(model)
}

class UnsupportedRuleModel(model: RuleModel) :
    RuntimeException("No processor for '${model.name}' of type '${model::class.simpleName}' was registered!")

class Processor @Inject constructor(private val ruleProcessorRegistry: RuleProcessorRegistry) {
    fun process(execution: Execution<RuleModel>): RuleResult =
        ruleProcessorRegistry.getRuleProcessor(execution.rule).build(execution)

    fun retrieveDependencyReferences(rule: RuleModel) =
        ruleProcessorRegistry.getRuleProcessor(rule).dependencyReferences(rule)
}
