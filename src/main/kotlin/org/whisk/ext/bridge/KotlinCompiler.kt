package org.whisk.ext.bridge

import java.nio.file.Path

interface KotlinCompiler {
    fun compile(
            cacheDir: Path,
            srcs: List<String>,
            compileClasspath: List<String>,
            kaptAPClasspath: List<String>,
            plugins: List<String>,
            targetDirectory: Path,
            kaptSources: Path,
            kaptClasses: Path,
            kaptStubs: Path,
            kaptKotlinSources: Path,
            friendPaths: List<Path>,
            additionalParameters: List<String>): Boolean
}