package org.whisk

import dagger.Component
import dagger.Module
import org.whisk.buildlang.BuildLangResolver
import org.whisk.execution.GraphBuilder
import org.whisk.rule.Processor
import javax.inject.Singleton

@Singleton
@Component(modules = [org.whisk.Module::class])
interface Application {
    fun processor(): Processor
    fun graphBuilder(): GraphBuilder
    fun resolver(): BuildLangResolver
    fun buildProperties(): BuildProperties
    fun cleaner(): Cleaner
}

@Module
interface Module {
    @Module
    companion object
}

