package org.whisk.ext.impl

import junit.framework.TestCase
import org.junit.Test
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.whisk.ext.bridge.UnitTester
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

class JUnit4Runner : UnitTester {
    override fun test(classesDir: Path): Int {

        val fileSystem = FileSystems.getDefault()
        val classMatcher = fileSystem.getPathMatcher("glob:**.class")
        val classes = Files.walk(classesDir).use { path ->
            path.filter { Files.isRegularFile(it) && classMatcher.matches(it) && !it.toString().contains("$") }
                    .map { candidate ->
                        val rel = classesDir.relativize(candidate)
                        JUnit4Runner::class.java.classLoader.loadClass(
                                rel.toString()
                                        .replace('/', '.')
                                        .replace(".class", "")
                        )
                    }.toList()
        }

        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TextListener(System.out))
        val classesToTest = classes.filter {
            it.isAssignableFrom(TestCase::class.java) ||
                    it.methods.any { it.isAnnotationPresent(Test::class.java) }
        }.toTypedArray()
        return jUnitCore.run(*classesToTest).failureCount
    }
}