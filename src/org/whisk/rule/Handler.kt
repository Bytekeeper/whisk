package org.whisk.rule

import junit.framework.TestCase
import org.apache.logging.log4j.LogManager
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith
import org.whisk.kotlin.KotlinCompiler
import org.whisk.model.*
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.streams.toList


class InvalidChecksumError(message: String) : Exception(message)

data class RuleResult(
    val fromRule: RuleModel,
    val files: List<String>
)

data class DependencyReferences(val refs: Map<Any, List<String>>)

interface RuleHandler<T : RuleModel> {
    fun build(execution: Execution<T>): RuleResult
    fun dependencyReferences(rule: T): DependencyReferences =
        DependencyReferences(mapOf("DEPS" to rule.deps))
}

internal fun download(whiskDir: Path, url: URL): Path {
    val log = LogManager.getLogger()
    val targetFile = whiskDir.resolve(url.path.substring(1))
    if (targetFile.toFile().exists()) {
        log.debug("{} exists, not downloading...", targetFile)
    } else {
        log.info("Downloading {}...", targetFile)
        Files.createDirectories(targetFile.parent)
        url.openStream().use { content ->
            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return targetFile
}

class PrebuiltJarHandler @Inject constructor() : RuleHandler<PrebuiltJar> {
    override fun build(
        execution: Execution<PrebuiltJar>
    ): RuleResult {
        val rule = execution.rule
        if (!File(rule.binary_jar).exists()) throw java.lang.IllegalStateException("${rule.name} file does not exist!")
        return RuleResult(rule, listOf(rule.binary_jar))
    }

}

class RemoteFileHandler @Inject constructor() : RuleHandler<RemoteFile> {
    private val log = LogManager.getLogger()

    override fun build(
        execution: Execution<RemoteFile>
    ): RuleResult {
        val rule = execution.rule
        val whiskDir = execution.cacheDir
        val url = URL(rule.url)
        val targetFile = download(whiskDir, url)
        return RuleResult(rule, listOf(targetFile.toAbsolutePath().toString()))
    }

}

class JavaBinaryHandler @Inject constructor() : RuleHandler<JavaBinary> {
    private val log = LogManager.getLogger()

    override fun build(
        execution: Execution<JavaBinary>
    ): RuleResult {
        val rule = execution.rule
        val ruleInput = execution.ruleInput
        val whiskOut = execution.targetPath
        val jarDir = whiskOut.resolve("jar")

        val jarName = jarDir.resolve("${rule.name}.jar")
        val deps = ruleInput.allResults()

        JarOutputStream(Files.newOutputStream(jarName))
            .use { out ->
                val usedNames = mutableSetOf<String>()
                rule.mainClass?.let { mainClass ->
                    out.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
                    val writer = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
                    writer.print("Main-Class: ")
                    writer.println(mainClass)
                    writer.flush()
                    usedNames += "META-INF/"
                    usedNames += "META-INF/MANIFEST.MF"
                }

                deps.forEach {
                    it.files.forEach { file ->
                        if (!usedNames.contains(file)) {
                            if (file.endsWith(".jar")) {
                                JarInputStream(Files.newInputStream(Paths.get(file)))
                                    .use { jar ->
                                        var entry = jar.nextJarEntry
                                        while (entry != null) {
                                            if (!usedNames.contains(entry.name)) {
                                                out.putNextEntry(JarEntry(entry.name))
                                                jar.copyTo(out)
                                                usedNames += entry.name
                                            } else {
                                                log.warn("Duplicate file $file:${entry.name}")
                                            }
                                            entry = jar.nextJarEntry
                                        }
                                    }
                            } else {
                                out.putNextEntry(JarEntry(file))
                                Files.copy(Paths.get(file), out)
                            }
                            usedNames += file
                        } else {
                            log.warn("Duplicate file $file")
                        }
                    }
                }
            }
        return RuleResult(rule, listOf(jarName.toString()))
    }

}

class KotlinCompileHandler @Inject constructor(private val kotlinCompiler: KotlinCompiler) :
    RuleHandler<KotlinCompile> {
    private val DEPS = "deps"
    private val KAPT_DEPS = "kapt_deps"
    private val EXPORTED_DEPS = "exported_deps"
    private val PROVIDED_DEPS = "provided_deps"

    override fun build(
        execution: Execution<KotlinCompile>
    ): RuleResult {
        val rule = execution.rule
        val ruleInput = execution.ruleInput

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        val jarDir = whiskOut.resolve("jar")
//
        val matcher = org.whisk.PathMatcher.toRegex(rule.srcs[0])
        val base = Paths.get(".")
        val srcs = Files.walk(base)
            .use {
                it.map { base.relativize(it) }
                    .filter {
                        matcher.matches(it.toString())
                    }.toList()
            }

        val deps = ruleInput.results[DEPS] ?: emptyList()
        val exportedDeps: List<RuleResult> = ruleInput.results[EXPORTED_DEPS] ?: emptyList()
        val providedDeps: List<RuleResult> = ruleInput.results[PROVIDED_DEPS] ?: emptyList()
        val dependencies = (deps + exportedDeps + providedDeps).flatMap { it.files }
        val kapt = (ruleInput.results[KAPT_DEPS] ?: emptyList()).flatMap { it.files }
        val kaptClasspath = dependencies//.filter { !it.contains("kotlin-compiler") }
        val params = srcs.map { it.toString() }
        kotlinCompiler.compile(params, dependencies, kaptClasspath, kapt, classesDir.toString())

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${rule.name}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
            .use { out ->
                Files.walk(classesDir).use { pathStream ->
                    pathStream
                        .forEach { path ->
                            val relativePath = classesDir.relativize(path)
                            if (Files.isRegularFile(path)) {
                                out.putNextEntry(JarEntry(relativePath.toString()));
                                Files.copy(path, out)
                            } else if (Files.isDirectory(path)) {
                                out.putNextEntry(JarEntry("$relativePath/"))
                            }
                        }
                }
            }

        val exportedFiles = exportedDeps.flatMap { it.files }
        return RuleResult(rule, exportedFiles + jarName.toString())
    }

    override fun dependencyReferences(rule: KotlinCompile): DependencyReferences =
        DependencyReferences(
            mapOf(
                DEPS to (rule.deps - rule.exported_deps),
                KAPT_DEPS to rule.kapt_deps,
                EXPORTED_DEPS to rule.exported_deps,
                PROVIDED_DEPS to rule.provided_deps
            )
        )
}

class KotlinTestHandler @Inject constructor(private val kotlinCompiler: KotlinCompiler) : RuleHandler<KotlinTest> {
    override fun build(
        execution: Execution<KotlinTest>
    ): RuleResult {
        val rule = execution.rule
        val ruleInput = execution.ruleInput
        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("test-classes")
        val jarDir = whiskOut.resolve("jar")

        val fileSystem = FileSystems.getDefault()
        val sourceMatcher = rule.srcs.map { fileSystem.getPathMatcher("glob:$it") }
            .let { matchers -> { path: Path -> matchers.any { matcher -> matcher.matches(path) } } }
        val base = execution.modulePath
        val srcs = Files.walk(base)
            .use {
                it.filter { sourceMatcher(base.relativize(it))}.toList()
            }

        val dependencies = ruleInput.allResults().flatMap { it.files }
        val params = srcs.map { it.toString() }

        kotlinCompiler.compile(params, dependencies, emptyList(), emptyList(), classesDir.toString())

        val cl = URLClassLoader(((dependencies.map {
            File(it).toURI().toURL()
        } + classesDir.toUri().toURL()).toTypedArray()))


        val testAnnotation = try {
            cl.loadClass(org.junit.Test::javaClass.name) as Class<Annotation>
        } catch (e: ClassNotFoundException) {
            null
        }
        val runWith = try {
            cl.loadClass(RunWith::javaClass.name) as Class<Annotation>
        } catch (e: ClassNotFoundException) {
            null
        }
        val testCase = try {
            cl.loadClass(TestCase::javaClass.name)
        } catch (e: ClassNotFoundException) {
            null
        }
        val classMatcher = fileSystem.getPathMatcher("glob:**.class")
        val classes = Files.walk(classesDir).use { path ->
            path.filter { Files.isRegularFile(it) && classMatcher.matches(it) && !it.toString().contains("$") }
                .map { candidate ->
                    val rel = classesDir.relativize(candidate)
                    cl.loadClass(
                        rel.toString()
                            .replace('/', '.')
                            .replace(".class", "")
                    )
                }.filter { c ->
                    (runWith == null || c.isAnnotationPresent(runWith))
                            && (testCase == null || testCase.isAssignableFrom(c))
                            && (testAnnotation == null || c.methods.any { it.isAnnotationPresent(testAnnotation) })
                }.toList().toTypedArray()
        }

        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TextListener(System.out))
        val result = jUnitCore.run(*classes)

        return RuleResult(rule, emptyList())
    }
}

