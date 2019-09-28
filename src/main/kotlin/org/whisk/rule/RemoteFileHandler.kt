package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.DownloadManager
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.RemoteFile
import java.net.URL
import javax.inject.Inject

class RemoteFileHandler @Inject constructor(private val downloadManager: DownloadManager) : RuleExecutor<RemoteFile> {
    private val log = LogManager.getLogger()

    override fun execute(
            execution: ExecutionContext<RemoteFile>
    ): RuleResult {
        val rule = execution.ruleParameters
        val whiskDir = execution.cacheDir
        val url = URL(rule.url.string)
        val targetFile = downloadManager.download(whiskDir, url)
        return Success(listOf(FileResource(targetFile.toAbsolutePath(), source = rule)))
    }

}