package org.whisk.rule

import org.whisk.DownloadManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.ProtocolCompile
import org.whisk.unzip
import java.net.URL
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import javax.inject.Inject
import kotlin.streams.toList

class ProtobufCompilerHandler @Inject constructor(
        private val downloadManager: DownloadManager
) :
        RuleExecutor<ProtocolCompile> {

    override val name: String = "Protobuf Code Generation"

    override fun execute(execution: ExecutionContext<ProtocolCompile>): RuleResult {
        val protocDir = execution.cacheDir.resolve("protoc")
        if (!protocDir.toFile().exists()) {
            val download = downloadManager.download(
                    protocDir,
                    URL("https://github.com/protocolbuffers/protobuf/releases/download/v3.9.1/protoc-3.9.1-linux-x86_64.zip")
            )
            download.unzip(protocDir)
        }
        val rule = execution.ruleParameters

        val protoc = protocDir.resolve("bin").resolve("protoc")
        Files.setPosixFilePermissions(protoc, setOf(PosixFilePermission.OWNER_EXECUTE))
        val params = mutableListOf(protoc.toString())
        params += rule.imports.map { "-I${it.string}" }
        params += "--java_out"
        val outputDir = execution.targetPath.resolve("gen").resolve("protobuf")
        Files.createDirectories(outputDir)
        params += outputDir.toString()
        params += rule.srcs.map { it.string }
        val protocProcess = ProcessBuilder().command(params)
                .inheritIO()
                .start()
        val exitCode = protocProcess.waitFor()

        return if (exitCode == 0) {
            val result = Files.walk(outputDir).use { pathStream ->
                pathStream.filter { Files.isRegularFile(it) }
                        .map { FileResource(it.toAbsolutePath(), source = rule) }.toList()
            }
            Success(result)
        } else Failed()
    }
}