package org.whisk.model

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.reflect.KClass

interface RuleModel {
    val name: String
}

interface RuleParser {
    fun parse(source: InputStream): Map<String, RuleModel>
    fun parse(file: Path): Map<String, RuleModel> = Files.newInputStream(file).use { parse(it) }
}

data class KotlinCompile(
        override val name: String,
        val srcs: List<String> = listOf("src/**/*.kt"),
        val cp: List<String> = emptyList(),
        var exported_deps: List<String> = emptyList(),
        val kapt_processors: List<String> = emptyList(),
        val plugins: List<String> = emptyList(),
        val provided_deps: List<String> = emptyList()
) : RuleModel

data class JavaCompile(
        override val name: String,
        val srcs: List<String> = listOf("src/**/*.java"),
        val cp: List<String> = emptyList(),
        var exported_deps: List<String> = emptyList(),
        val apt_deps: List<String> = emptyList(),
        val provided_deps: List<String> = emptyList()
) : RuleModel

data class KotlinTest(
        override val name: String,
        val srcs: List<String> = listOf("test/**/*.kt"),
        val cp: List<String> = emptyList()
) : RuleModel

data class PrebuiltJar(
        override val name: String,
        val binary_jar: String
) : RuleModel

data class RemoteFile(
        override val name: String,
        val url: String,
        val sha1: String
) : RuleModel

data class JavaBinary(
        override val name: String,
        val files: List<String>,
        val main_class: String?
) : RuleModel

data class MavenLibrary(
        override val name: String,
        val artifacts: List<String>,
        val repositoryUrl: String?
) : RuleModel

data class ProtobufCompile(
        override val name: String,
        val srcs: List<String> = listOf("proto/**.proto"),
        val imports: List<String>
) : RuleModel

class RuleModelRegistry @Inject constructor() {
    private val models = mutableMapOf<String, KClass<out RuleModel>>()

    init {
        register<KotlinCompile>()
        register<KotlinTest>()
        register<PrebuiltJar>()
        register<RemoteFile>()
        register<JavaBinary>()
        register<MavenLibrary>()
        register<JavaCompile>()
        register<ProtobufCompile>()
    }

    inline fun <reified T : RuleModel> register() {
        val kClass = T::class
        register(kClass)

    }

    fun <T : RuleModel> register(kClass: KClass<T>) {
        models[kClass.simpleName!!.replace(Regex("([^_A-Z])([A-Z])"), "$1_$2").toLowerCase()] = kClass
    }

    fun getRuleClass(name: String) = models[name] ?: throw UnsupportedRule(name)
}

class UnsupportedRule(val ruleName: String) : RuntimeException("No rule '$ruleName' was registered!")