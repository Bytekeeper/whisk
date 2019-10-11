package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.DownloadManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.ProtocolCompile
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import org.whisk.unzip
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import javax.inject.Inject
import kotlin.streams.toList

class ProtobufCompilerHandler @Inject constructor(
        private val downloadManager: DownloadManager,
        private val ruleInvocationStore: RuleInvocationStore
) :
        RuleExecutor<ProtocolCompile> {
    private val log = LogManager.getLogger()

    override val name: String = "Protobuf Code Generation"

    override fun execute(execution: ExecutionContext<ProtocolCompile>): RuleResult {
        val rule = execution.ruleParameters

        val lastInvocation = ruleInvocationStore.readLastInvocation(execution)
        val currentRuleCall = rule.toStorageFormat()

        if (currentRuleCall == lastInvocation?.ruleCall) {
            log.info("No changes, not running protobuf compiler.")
            return Success(lastInvocation.resultList.toResources(rule))
        }

        val protocDir = execution.cacheDir.resolve("protoc")
        ensureProtocIsAvailable(protocDir)
        val protoc = protocDir.resolve("bin").resolve("protoc")
        try {
            Files.setPosixFilePermissions(protoc, setOf(PosixFilePermission.OWNER_EXECUTE))
        } catch (e: UnsupportedOperationException) {
            // No biggie, 'tis Windows?
        }
        val params = mutableListOf(protoc.toString())
        params += rule.imports.map { "-I${it.string}" }
        val outputDir = execution.targetPath.resolve("gen").resolve("protobuf")
        Files.createDirectories(outputDir)
        params += "--${rule.output_type.string}=$outputDir"
        params += rule.srcs.map(FileResource::string)
        val protocProcess = ProcessBuilder().command(params)
                .inheritIO()
                .start()
        val exitCode = protocProcess.waitFor()

        return if (exitCode == 0) {
            val result = Files.walk(outputDir).use { pathStream ->
                pathStream.filter { Files.isRegularFile(it) }
                        .map { FileResource(it.toAbsolutePath(), source = rule) }.toList()
            }
            ruleInvocationStore.writeNewInvocation(execution, currentRuleCall, result)
            Success(result)
        } else Failed()
    }

    private fun ensureProtocIsAvailable(protocDir: Path) {
        if (!protocDir.toFile().exists()) {
            val download = downloadManager.download(
                    protocDir,
                    URL("https://github.com/protocolbuffers/protobuf/releases/download/v3.9.1/protoc-3.9.1-linux-x86_64.zip")
            )
            download.unzip(protocDir)
        }
    }
}