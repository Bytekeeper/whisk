package org.whisk

import org.whisk.model.FileResource
import java.io.InputStream
import java.math.BigInteger
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipOutputStream

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

fun <T> withTempFile(block: (Path) -> T): T {
    val tempFile = Files.createTempFile("whisk", "data")
    try {
        return block(tempFile)
    } finally {
        Files.delete(tempFile)
    }
}

fun Path.clean(): Boolean {
    System.err.println("Would delete $this recursively")
    return true
//    toFile().deleteRecursively()
}

fun Iterable<FileResource>.copy(targetDir: Path) = forEach {
    if (it.root == it.path)
        error("Copying of files without relative base is not possible.")
    Files.copy(it.path, targetDir.resolve(it.relativePath))
}

fun Iterable<FileResource>.copy(out: JarOutputStream) = forEach {
    if (it.root == it.path)
        error("Copying of files without relative base is not possible.")
    out.putNextEntry(JarEntry(it.relativePath.toString()))
    Files.copy(it.path, out)
}