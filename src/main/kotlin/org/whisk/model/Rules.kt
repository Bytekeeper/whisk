package org.whisk.model

import javax.inject.Inject
import kotlin.reflect.KClass

interface RuleParameters

data class AntlrGen(
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
        val additional_parameters: List<StringResource>
) : RuleParameters

data class JavaCompile(
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val apt_deps: List<FileResource> = emptyList()
) : RuleParameters

data class KotlinTest(
        val compiler: List<FileResource>,
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        val additional_parameters: List<StringResource>
) : RuleParameters

data class PrebuiltJar(
        val binary_jar: StringResource
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
        val srcs: List<FileResource>,
        val imports: List<FileResource>
) : RuleParameters

data class Exec(
        val work_dir: FileResource?,
        val src: FileResource,
        val arguments: List<Resource>
) : RuleParameters

data class Glob(val pattern: List<StringResource>) : RuleParameters {
    init {
        check(pattern.isNotEmpty()) { "glob() call requires at least one argument" }
    }
}

data class RGlob(val pattern: List<StringResource>, val root: FileResource) : RuleParameters

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
        register<ProtocolCompile>()
        register<Glob>()
        register<AntlrGen>()
        register<Exec>()
        register<RGlob>()
    }

    inline fun <reified T : RuleParameters> register() {
        val kClass = T::class
        register(kClass)

    }

    fun <T : RuleParameters> register(kClass: KClass<T>) {
        models[kClass.simpleName!!.replace(Regex("([^_A-Z])([A-Z])"), "$1_$2").toLowerCase()] = kClass
    }

    fun getRuleClass(name: String) = models[name] ?: throw UnsupportedRule(name)
}

class UnsupportedRule(val ruleName: String) : RuntimeException("No rule '$ruleName' was registered!")