package org.whisk.ext

import org.whisk.ext.bridge.AntlrTool
import org.whisk.ext.bridge.KTlintRunner
import org.whisk.ext.bridge.KotlinCompiler
import org.whisk.ext.bridge.UnitTester
import org.whisk.ext.impl.AntlrToolImpl
import org.whisk.ext.impl.JUnit4Runner
import org.whisk.ext.impl.KTlintRunnerImpl
import org.whisk.ext.impl.KotlinCompilerImpl
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.tools.ToolProvider

/**
 * Adapter to load actual implementations without requiring all dependencies of them in whisk's class path.
 * <em>
 * A bit of evil: loadClass is called with the actual class and not just the name. This will make the current classloader load the class file,
 * but ExtClassLoader is a parent last classloader and will load the class again. Since only the interfaces are "passed" around, it should be fine.
 * </em>
 */
class ExtAdapter @Inject constructor() {
    fun kotlinCompiler(compilerClassPath: List<URL>) =
            kotlinCompilerCache.computeIfAbsent(CacheKey(compilerClassPath, ToolProvider.getSystemToolClassLoader())) {
                ExtClassLoader(compilerClassPath.toTypedArray(), ToolProvider.getSystemToolClassLoader())
                        .loadClass(KotlinCompilerImpl::class.java.name).newInstance() as KotlinCompiler
            }

    fun antlrTool(toolClassPath: List<URL>) =
            ExtClassLoader(toolClassPath.toTypedArray())
                    .loadClass(AntlrToolImpl::class.java.name).newInstance() as AntlrTool

    fun unitTestRunner(unitLibraryClassPath: List<URL>) =
            ExtClassLoader(unitLibraryClassPath.toTypedArray())
                    .loadClass(JUnit4Runner::class.java.name).newInstance() as UnitTester

    fun ktLinter(cp: List<URL>) =
            ktLinterCache.computeIfAbsent(CacheKey(cp, null)) {
                ExtClassLoader(cp.toTypedArray())
                        .loadClass(KTlintRunnerImpl::class.java.name).newInstance() as KTlintRunner
            }

    companion object {
        private val kotlinCompilerCache = ConcurrentHashMap<CacheKey, KotlinCompiler>()
        private val ktLinterCache = ConcurrentHashMap<CacheKey, KTlintRunner>()
    }

    private data class CacheKey(private val classpath: List<URL>, private val parent: ClassLoader?)
}