package org.whisk.ext.bridge

import java.nio.file.Path

interface KotlinCompiler {
    fun compile(compilerClasspath: List<Path>,
                srcs: List<String>,
                compileClasspath: List<String>,
                kaptAPClasspath: List<String>,
                plugins: List<String>,
                classes: Path,
                kaptSources: Path,
                kaptClasses: Path,
                kaptKotlinSources: Path,
                additionalParameters: List<String>): Boolean
}