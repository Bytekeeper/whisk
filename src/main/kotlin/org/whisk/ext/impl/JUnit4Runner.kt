package org.whisk.ext.impl

import junit.framework.TestCase
import org.junit.Test
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.whisk.ext.bridge.UnitTester

class JUnit4Runner : UnitTester {
    override fun test(classes: List<Class<*>>): Int {
        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TextListener(System.out))
        val classesToTest = classes.filter {
            it.isAssignableFrom(TestCase::class.java) ||
                    it.methods.any { it.isAnnotationPresent(Test::class.java) }
        }.toTypedArray()
        return jUnitCore.run(*classesToTest).failureCount
    }
}