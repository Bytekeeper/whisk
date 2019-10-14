package org.whisk.model

import org.whisk.buildlang.NO_MODULE
import javax.inject.Inject
import kotlin.reflect.KClass

interface RuleParameters

data class AntlrGen(
        val tool: List<FileResource>,
        val srcs: List<FileResource>,
        val arguments: List<StringResource>
) : RuleParameters

data class KotlinCompile(
        val compiler: List<FileResource>,
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val kapt_processors: List<FileResource> = emptyList(),
        val plugins: List<FileResource> = emptyList(),
        val friend_paths: List<FileResource> = emptyList(),
        val additional_parameters: List<StringResource>
) : RuleParameters

data class KotlinTest(
        val compiler: List<FileResource>,
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val kapt_processors: List<FileResource> = emptyList(),
        val plugins: List<FileResource> = emptyList(),
        val friend_paths: List<FileResource>,
        val additional_parameters: List<StringResource>
) : RuleParameters

data class JavaCompile(
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val apt_deps: List<FileResource> = emptyList()
) : RuleParameters

data class JavaTest(
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val apt_deps: List<FileResource> = emptyList()
) : RuleParameters

data class PrebuiltJar(
        val binary_jar: FileResource
) : RuleParameters

data class RemoteFile(
        val url: StringResource
) : RuleParameters

data class BuildJar(
        val name: StringResource?,
        val files: List<FileResource>,
        val main_class: StringResource?
) : RuleParameters

data class MavenLibrary(
        val artifacts: List<StringResource>,
        val repository_urls: List<StringResource>,
        val scopes: List<StringResource> = emptyList()
) : RuleParameters

data class ProtocolCompile(
        val dist: FileResource?,
        val srcs: List<FileResource>,
        val imports: List<FileResource>,
        val output_type: StringResource = StringResource("java_out", null, NO_MODULE)
) : RuleParameters

data class Exec(
        val work_dir: FileResource?,
        val command: FileResource,
        val arguments: List<Resource>
) : RuleParameters

data class Glob(val pattern: List<StringResource>) : RuleParameters
data class RGlob(val pattern: List<StringResource>, val root: FileResource) : RuleParameters
data class OnWindows(val passthrough: List<Resource>) : RuleParameters
data class OnLinux(val passthrough: List<Resource>) : RuleParameters

class RuleRegistry @Inject constructor() {
    private val models = mutableMapOf<String, KClass<out RuleParameters>>()

    init {
        register<KotlinCompile>()
        register<KotlinTest>()
        register<PrebuiltJar>()
        register<RemoteFile>()
        register<BuildJar>()
        register<MavenLibrary>()
        register<JavaCompile>()
        register<JavaTest>()
        register<ProtocolCompile>()
        register<Glob>()
        register<AntlrGen>()
        register<Exec>()
        register<RGlob>()
        register<OnWindows>()
        register<OnLinux>()
    }

    inline fun <reified T : RuleParameters> register() {
        val kClass = T::class
        register(kClass)

    }

    fun <T : RuleParameters> register(kClass: KClass<T>) =
            register(kClass.simpleName!!.replace(Regex("([^_A-Z])([A-Z])"), "$1_$2").toLowerCase(), kClass)

    fun <T : RuleParameters> register(name: String, kClass: KClass<T>) {
        models[name] = kClass
    }

    fun getRuleClass(name: String) = models[name] ?: throw UnsupportedRule(name)
}

class UnsupportedRule(ruleName: String) : RuntimeException("No rule '$ruleName' was registered!")