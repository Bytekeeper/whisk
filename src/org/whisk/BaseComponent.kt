package org.whisk

import dagger.Binds
import dagger.Component
import dagger.Module
import org.whisk.model.RuleParser
import org.whisk.model.TomlRuleParser
import org.whisk.rule.Processor

@Component(modules = [org.whisk.Module::class])
interface Application {
    fun ruleParser(): RuleParser

    fun processor(): Processor
}

@Module
interface Module {
    @Binds
    fun ruleParser(parser: TomlRuleParser): RuleParser

    @Module
    companion object {
    }
}

