package org.whisk.ext.bridge

import java.nio.file.Path

interface UnitTester {
    fun test(classesDir: Path): Int
}