package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.java.ABI
import org.whisk.java.JavaCompiler
import org.whisk.model.FileResource
import org.whisk.model.JavaCompile
import org.whisk.model.nonRemoved
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject

class JavaCompileHandler @Inject constructor(private val javaCompiler: JavaCompiler,
                                             private val abi: ABI) :
        RuleExecutor<JavaCompile> {
    private val log = LogManager.getLogger()

    override val name: String = "Java Code Compilation"

    override fun execute(execution: ExecutionContext<JavaCompile>): RuleResult {
        val rule = execution.ruleParameters

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        Files.createDirectories(classesDir)
        val jarDir = whiskOut.resolve("jar")
//

        val dependencies = (rule.cp.nonRemoved + rule.exported_deps.nonRemoved)
                .map(FileResource::placeHolderOrReal)
                .map(Path::toFile)
        javaCompiler.compile(rule.srcs.nonRemoved.map(FileResource::file), dependencies, classesDir.toFile())

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
                                                    abiOut.putNextEntry(JarEntry(relativePath.toString()))
                                                    Files.copy(path, out)
                                                    val reduced = abi.toReducedABIClass(path)
                                                    abiOut.write(reduced, 0, reduced.size)
                                                } else if (path.root != path && Files.isDirectory(path)) {
                                                    out.putNextEntry(JarEntry("$relativePath/"))
                                                    abiOut.putNextEntry(JarEntry("$relativePath/"))
                                                }
                                            }
                                }
                            }
                }

        return Success(rule.exported_deps + FileResource(jarName.toAbsolutePath(), source = rule, placeHolder = abiJarName.toAbsolutePath()))
    }
}