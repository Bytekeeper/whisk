package org.whisk.rule

import org.whisk.execution.FileResource
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.ProtobufCompile
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import javax.inject.Inject
import kotlin.streams.toList

class ProtobufCompilerHandler @Inject constructor() :
        RuleExecutor<ProtobufCompile> {

    override fun execute(execution: Execution<ProtobufCompile>): RunnableFuture<RuleResult> {
        val protocDir = execution.cacheDir.resolve("protoc")
        if (!protocDir.toFile().exists()) {
            val download = download(
                    protocDir,
                    URL("https://github.com/protocolbuffers/protobuf/releases/download/v3.8.0/protoc-3.8.0-linux-x86_64.zip")
            )
            FileSystems.newFileSystem(download, null)
                    .use { zfs ->
                        val base = zfs.getPath("/")
                        Files.walk(base).use {
                            it.forEach { p ->
                                val target = protocDir.resolve(base.relativize(p).toString())
                                Files.createDirectories(target.parent)
                                if (Files.isRegularFile(p)) {
                                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                        }
                    }
        }
        val rule = execution.ruleParameters

        val protoc = protocDir.resolve("bin").resolve("protoc")
        Files.setPosixFilePermissions(protoc, setOf(PosixFilePermission.OWNER_EXECUTE))
        val params = mutableListOf(protoc.toString())
        params += rule.imports.map { "-I$it" }
        params += "--java_out"
        val outputDir = execution.targetPath.resolve("gen").resolve("protobuf")
        Files.createDirectories(outputDir)
        params += outputDir.toString()
        params += rule.srcs.map { it.absolutePath }
        val protocProcess = ProcessBuilder().command(params)
                .inheritIO()
                .start()
        protocProcess.waitFor()

        val result = Files.walk(outputDir).use { pathStream ->
            pathStream.filter { Files.isRegularFile(it) }.map { FileResource(it) }.toList()
        }
        return FutureTask { Success(result) }
    }
}