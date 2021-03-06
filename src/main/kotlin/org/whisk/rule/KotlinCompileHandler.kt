package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.copy
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.java.ABI
import org.whisk.java.JavaCompiler
import org.whisk.model.FileResource
import org.whisk.model.KotlinCompile
import org.whisk.model.StringResource
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.stream.Stream
import javax.inject.Inject
import kotlin.streams.toList

class KotlinCompileHandler @Inject constructor(private val javaCompiler: JavaCompiler,
                                               private val extAdapter: ExtAdapter,
                                               private val abi: ABI,
                                               private var changeManager: RuleInvocationStore) :
        RuleExecutor<KotlinCompile> {
    private val log = LogManager.getLogger()

    override val name: String = "Kotlin Code Compilation"

    override fun execute(
            execution: ExecutionContext<KotlinCompile>
    ): RuleResult {
        val rule = execution.ruleParameters

        val targetPath = execution.targetPath
        val classesDir = targetPath.resolve("classes")
        val kaptDir = targetPath.resolve("kapt")
        val kaptClasses = kaptDir.resolve("classes")
        val jarDir = targetPath.resolve("jar")
//
        val sourceFiles = rule.srcs.map(FileResource::string)
        if (sourceFiles.isEmpty()) {
            log.warn("No source files found in ${execution.goalFQN}")
            return Success(rule.exported_cp)
        }

        val lastInvocation = changeManager.readLastInvocation(execution)
        val currentCall = rule.toStorageFormat()

        if (lastInvocation?.ruleCall == currentCall) {
            log.info("No changes, not running kotlin compiler.")
            return Success(lastInvocation.resultList.toResources(rule))
        }

        val dependencies = (rule.cp + rule.exported_cp)
                .map(FileResource::placeHolderOrReal)
                .map(Path::toString)
        val kaptAPClasspath = rule.kapt_processors.map(FileResource::string)
        val plugins = (rule.plugins + rule.compiler).map(FileResource::string)

        val kotlinCompiler = extAdapter.kotlinCompiler(rule.compiler.map(FileResource::url))

        val succeeded = kotlinCompiler.compile(
                targetPath.resolve("kotlin-cache"),
                sourceFiles,
                dependencies,
                kaptAPClasspath,
                plugins,
                classesDir,
                kaptDir.resolve("sources"),
                kaptClasses,
                kaptDir.resolve("stubs"),
                kaptDir.resolve("kotlinSources"),
                rule.friend_paths.map(FileResource::placeHolderOrReal),
                rule.additional_parameters.map(StringResource::string))
        if (!succeeded) return Failed()

        val javaSources = Files.walk(kaptDir.resolve("sources")).use { it.filter { Files.isRegularFile(it) }.map { it.toFile() }.toList() } +
                sourceFiles.filter { it.endsWith(".java") }.map { File(it) }
        if (javaSources.isNotEmpty()) {
            javaCompiler.compile(javaSources, dependencies.map { File(it) } + classesDir.toFile(), classesDir.toFile())
        }

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${rule.jar_name ?: execution.goalName}.jar")
        val abiJarName = jarDir.resolve("ABI-${execution.goalName}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
                .use { out ->
                    rule.res.copy(out)
                    JarOutputStream(Files.newOutputStream(abiJarName))
                            .use { abiOut ->
                                rule.main_class?.let {
                                    out.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
                                    val writer = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
                                    writer.print("Main-Class: ")
                                    writer.println(it.string)
                                    writer.flush()
                                }
                                val addToJar = { pathStream: Stream<Path> ->
                                    pathStream
                                            .forEach { path ->
                                                val relativePath = classesDir.relativize(path)
                                                if (Files.isRegularFile(path)) {
                                                    out.putNextEntry(JarEntry(relativePath.toString()))
                                                    Files.copy(path, out)
                                                    if (path.fileName.toString().endsWith(".class")) {
                                                        abiOut.putNextEntry(JarEntry(relativePath.toString()))
                                                        val reduced = abi.toReducedABIClass(path)
                                                        abiOut.write(reduced, 0, reduced.size)
                                                    }
                                                } else if (path.root != path && Files.isDirectory(path)) {
                                                    out.putNextEntry(JarEntry("$relativePath/"))
                                                    abiOut.putNextEntry(JarEntry("$relativePath/"))
                                                }
                                            }
                                }
                                Files.walk(classesDir).use(addToJar)
                                Files.walk(kaptClasses).use(addToJar)
                            }
                }

        val resources = rule.exported_cp +
                FileResource(jarName.toAbsolutePath(), jarDir.toAbsolutePath(), rule, abiJarName.toAbsolutePath())
        changeManager.writeNewInvocation(execution, currentCall, resources)
        return Success(resources)
    }
}