package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.java.ABI
import org.whisk.java.JavaCompiler
import org.whisk.model.FileResource
import org.whisk.model.JavaCompile
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject

class JavaCompileHandler @Inject constructor(private val javaCompiler: JavaCompiler,
                                             private val abi: ABI,
                                             private val ruleInvocationStore: RuleInvocationStore) :
        RuleExecutor<JavaCompile> {
    private val log = LogManager.getLogger()

    override val name: String = "Java Code Compilation"

    override fun execute(execution: ExecutionContext<JavaCompile>): RuleResult {
        val rule = execution.ruleParameters
        val targetPath = execution.targetPath

        val lastInvocation = ruleInvocationStore.readLastInvocation(execution)
        val currentCall = rule.toStorageFormat()

        if (lastInvocation?.ruleCall == currentCall) {
            log.info("No changes, not running javac compiler.")
            return Success(lastInvocation.resultList.toResources(rule))
        }

        val classesDir = targetPath.resolve("classes")
        Files.createDirectories(classesDir)
        val jarDir = targetPath.resolve("jar")
//

        val dependencies = (rule.cp + rule.exported_deps)
                .map(FileResource::placeHolderOrReal)
                .map(Path::toFile)

        javaCompiler.compile(rule.srcs.map(FileResource::file), dependencies, classesDir.toFile())

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${execution.goalName}.jar")
        val abiJarName = jarDir.resolve("ABI-${execution.goalName}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
                .use { out ->
                    JarOutputStream(Files.newOutputStream(abiJarName))
                            .use { abiOut ->
                                Files.walk(classesDir).use { pathStream ->
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
                            }
                }

        val resources = rule.exported_deps + FileResource(jarName.toAbsolutePath(), source = rule, placeHolder = abiJarName.toAbsolutePath())
        ruleInvocationStore.writeNewInvocation(execution, currentCall, resources)
        return Success(resources)
    }
}