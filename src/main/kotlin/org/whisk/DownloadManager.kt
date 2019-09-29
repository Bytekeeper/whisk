package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.rule.FailedToDownload
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class DownloadManager @Inject constructor() {
    private val lockedPaths = ConcurrentHashMap<Path, Any>()

    private fun locked(path: Path, dl: (Path) -> Path): Path {
        try {
            synchronized(lockedPaths.computeIfAbsent(path) { Any() }) {
                return dl(path)
            }
        } finally {
            lockedPaths.remove(path)
        }
    }


    internal fun download(target: Path, url: URL): Path {
        return locked(target) {
            val log = LogManager.getLogger()
            val targetFile = target.resolve(url.path.substring(1))
            if (targetFile.toFile().exists()) {
                log.debug("{} exists, not downloading...", targetFile)
            } else {
                log.info("Downloading {}...", targetFile)
                Files.createDirectories(targetFile.parent)
                url.openStream().use { content ->
                    Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
                }
            }
            lockedPaths.remove(target)
            targetFile
        }
    }

    internal fun download(target: Path, urls: List<URL>): Path {
        return locked(target) {
            val log = LogManager.getLogger()
            urls.mapNotNull { url ->
                val targetFile = target.resolve(url.path.substring(1))
                if (targetFile.toFile().exists()) {
                    log.debug("{} exists, not downloading...", targetFile)
                    targetFile
                } else null
            }.firstOrNull() ?: urls.mapNotNull { url ->
                val targetFile = target.resolve(url.path.substring(1))
                log.info("Downloading {}...", url)
                Files.createDirectories(targetFile.parent)
                try {
                    url.openStream().use { content ->
                        Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
                    }
                    targetFile
                } catch (e: Exception) {
                    null
                }
            }.firstOrNull()
            ?: throw FailedToDownload("Could not download ${target.fileName} from any location: ${urls.joinToString()}")
        }
    }
}