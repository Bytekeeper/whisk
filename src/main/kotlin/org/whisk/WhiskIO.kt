package org.whisk

import java.io.InputStream
import java.math.BigInteger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

fun <T> InputStream.readChunked(initial: T, handler: (T, ByteArray, Int) -> T): T {
    val buf = ByteArray(4096)
    var read = read(buf)
    var agg = initial
    while (read >= 0) {
        agg = handler(agg, buf, read)
        read = read(buf)
    }
    return agg
}

fun Path.sha1() = Files.newInputStream(this)
    .use {ins ->
        ins.readChunked(MessageDigest.getInstance("SHA1")) { md, buf, read ->
            md.update(buf, 0, read)
            md
        }.digest()
    }

fun ByteArray.toHex() = BigInteger(1, this).toString(16).padStart(40, '0')

fun Path.unzip(targetDir: Path) = FileSystems.newFileSystem(this, null)
        .use { zfs ->
            val base = zfs.getPath("/")
            Files.walk(base).use {
                it.forEach { p ->
                    val target = targetDir.resolve(base.relativize(p).toString())
                    Files.createDirectories(target.parent)
                    if (Files.isRegularFile(p)) {
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }