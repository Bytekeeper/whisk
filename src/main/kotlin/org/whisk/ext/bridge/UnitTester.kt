package org.whisk.ext.bridge

interface UnitTester {
    fun test(classes: List<Class<*>>): Int
}