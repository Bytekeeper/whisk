package org.whisk

import java.io.InputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object PathMatcher {
    fun toRegex(path: String) =
            path.replace(".", "\\.")
                .replace("?", "\\w")
                .replace("**", ".*")
                .replace("(?<!\\.)\\*".toRegex(), "[^/]+")
}

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