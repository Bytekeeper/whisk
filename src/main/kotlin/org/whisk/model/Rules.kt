package org.whisk.model

import org.whisk.execution.FileResource
import org.whisk.execution.StringResource
import javax.inject.Inject
import kotlin.reflect.KClass

interface RuleParameters

data class KotlinCompile(
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val kapt_processors: List<FileResource> = emptyList(),
        val plugins: List<FileResource> = emptyList()
) : RuleParameters

data class JavaCompile(
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList(),
        var exported_deps: List<FileResource> = emptyList(),
        val apt_deps: List<FileResource> = emptyList()
) : RuleParameters

data class KotlinTest(
        val srcs: List<FileResource>,
        val cp: List<FileResource> = emptyList()
) : RuleParameters

data class PrebuiltJar(
        val binary_jar: FileResource
) : RuleParameters

data class RemoteFile(
        val url: StringResource,
        val sha1: StringResource
) : RuleParameters

data class BuildJar(
        val files: List<FileResource>,
        val main_class: StringResource?
) : RuleParameters

data class MavenLibrary(
        val artifacts: List<StringResource>,
        val repositoryUrl: StringResource?
) : RuleParameters

data class ProtobufCompile(
        val srcs: List<FileResource>,
        val imports: List<FileResource>
) : RuleParameters

data class Glob(val srcs: List<String>) : RuleParameters

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
        register<ProtobufCompile>()
        register<Glob>()
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