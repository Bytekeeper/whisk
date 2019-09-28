package org.whisk.ext

interface UnitTester {
    fun test(classes: Array<Class<*>>): Int
}