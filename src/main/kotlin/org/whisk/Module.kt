package org.whisk

import dagger.Component
import dagger.Module
import org.whisk.buildlang.BuildLangResolver
import org.whisk.execution.GraphBuilder
import org.whisk.rule.Processor

@Component(modules = [org.whisk.Module::class])
interface Application {
    fun processor(): Processor
    fun graphBuilder(): GraphBuilder
    fun resolver(): BuildLangResolver
}

@Module
interface Module {
    @Module
    companion object
}

