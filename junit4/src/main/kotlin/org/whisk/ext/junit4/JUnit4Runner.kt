package org.whisk.ext.junit4

import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.whisk.ext.UnitTester

class JUnit4Runner : UnitTester {
    override fun test(classes: Array<Class<*>>): Int {
        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TextListener(System.out))
        return jUnitCore.run(*classes).failureCount
    }
}