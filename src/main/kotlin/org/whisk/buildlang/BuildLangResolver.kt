package org.whisk.buildlang

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.whisk.model.RuleParameters
import org.whisk.model.RuleRegistry
import java.nio.file.Path
import javax.inject.Inject
import kotlin.reflect.KClass


data class SourceRef<out S>(val module: String, val source: S)
interface WithSourceRef<out S> {
    val source: SourceRef<S>
}

data class ResolvedGoal(override val source: SourceRef<GoalDeclaration>, val name: String, var value: ResolvedValue<Value>?) : WithSourceRef<GoalDeclaration>
data class ResolvedRule(override val source: SourceRef<RuleDefinition>, val name: String, val params: List<ResolvedRuleParamDef>, var value: ResolvedValue<Value>?, var nativeRule: KClass<out RuleParameters>?, val anon: Boolean) : WithSourceRef<RuleDefinition>

interface ResolvedValue<out S> : WithSourceRef<S>
data class ResolvedStringValue(override val source: SourceRef<StringValue>, val value: String) : ResolvedValue<StringValue>
data class ResolvedListValue(override val source: SourceRef<ListValue>, val items: List<ResolvedValue<Value>> = emptyList()) : ResolvedValue<ListValue>
data class ResolvedRuleCall(override val source: SourceRef<RuleCall>, val rule: ResolvedRule, val params: List<ResolvedRuleParam>) : ResolvedValue<RuleCall>
data class ResolvedGoalCall(override val source: SourceRef<RefValue>, val goal: ResolvedGoal) : ResolvedValue<RefValue>
data class ResolvedRuleParam(override val source: SourceRef<RuleParam>, val param: ResolvedRuleParamDef, val value: ResolvedValue<Value>) : WithSourceRef<RuleParam>
data class ResolvedRuleParamDef(override val source: SourceRef<RuleParamDef>, val name: String, val type: ParamType, val optional: Boolean) : WithSourceRef<RuleParamDef>

interface SymbolTable {
    fun resolveGoal(modules: List<String>, name: String): ResolvedGoal
    fun resolveRule(modules: List<String>, name: String): ResolvedRule
}

class ModuleTable {
    private val goals = mutableMapOf<String, ResolvedGoal>()
    private val rules = mutableMapOf<String, ResolvedRule>()

    fun resolveGoal(name: String): ResolvedGoal? = goals[name]
    fun resolveRule(name: String): ResolvedRule? = rules[name]

    fun registerGoal(goal: ResolvedGoal) {
        goals[goal.name] = goal
    }

    fun registerRule(rule: ResolvedRule) {
        rules[rule.name] = rule
    }

    fun exposedGoals(): List<ResolvedGoal> =
            goals.values.toList()
}

class GlobalTable : SymbolTable {
    private val modules = mutableMapOf<String, ModuleTable>()

    override fun resolveGoal(modules: List<String>, name: String): ResolvedGoal =
            (modules + "").mapNotNull { this.modules[it]?.resolveGoal(name) }.single()

    override fun resolveRule(modules: List<String>, name: String): ResolvedRule =
            (modules + "").mapNotNull { this.modules[it]?.resolveRule(name) }.singleOrNull()
                    ?: throw RuleNotFoundException("Unknown rule $name")

    fun exposedGoalsOf(module: String) =
            modules[module]?.exposedGoals() ?: emptyList()

    fun registerGoal(goal: ResolvedGoal) =
            modules.computeIfAbsent(goal.source.module) { ModuleTable() }.registerGoal(goal)

    fun registerRule(rule: ResolvedRule) =
            modules.computeIfAbsent(rule.source.module) { ModuleTable() }.registerRule(rule)
}

class LocalTable(private val parent: GlobalTable) : SymbolTable {
    private val module = ModuleTable()

    override fun resolveGoal(modules: List<String>, name: String): ResolvedGoal =
            module.resolveGoal(name) ?: parent.resolveGoal(modules, name)

    override fun resolveRule(modules: List<String>, name: String): ResolvedRule =
            module.resolveRule(name) ?: parent.resolveRule(modules, name)

    fun registerGoal(goal: ResolvedGoal) =
            module.registerGoal(goal)

    fun registerRule(rule: ResolvedRule) =
            module.registerRule(rule)
}

interface ModuleLoader {
    fun load(module: String): CharStream
}

class PathModuleLoader(private val basePath: Path) : ModuleLoader {
    override fun load(module: String): CharStream =
            CharStreams.fromPath(basePath.resolve(module.replace('.', '/')).resolve("WHISK.BL"))
}

class BuildLangResolver @Inject constructor(
        private val buildLangTransformer: BuildLangTransformer,
        private val ruleRegistry: RuleRegistry) {

    fun resolve(moduleLoader: ModuleLoader, module: String): List<ResolvedGoal> {
        val parsedModules = mutableSetOf<String>()
        val globalTable = GlobalTable()

        fun resolveInternal(module: String) {
            if (parsedModules.contains(module)) return
            parsedModules += module
            val localTable = LocalTable(globalTable)

            val lexer = BuildLangLexer(moduleLoader.load(module))
            val parser = BuildLangParser(CommonTokenStream(lexer))
            parser.removeErrorListeners()
            parser.addErrorListener(BLErrorListener())
            val buildFile = buildLangTransformer.buildFileFrom(parser.buildFile())
            val exposed = buildFile.export.exports.map { it.text }.toSet()

            buildFile.declarations.forEach { decl ->
                val goalName = decl.name.text
                val resolvedGoal = ResolvedGoal(SourceRef(module, decl), goalName, null)
                if (exposed.isEmpty() || exposed.contains(goalName))
                    globalTable.registerGoal(resolvedGoal)
                else
                    localTable.registerGoal(resolvedGoal)
            }
            buildFile.definitions.forEach { def ->
                val ruleName = def.name.text
                val params = def.ruleParamDefs
                        .map { ResolvedRuleParamDef(SourceRef(module, it), it.name.text, it.type, it.optional) }
                val ruleDef = ResolvedRule(SourceRef(module, def), ruleName, params, null, null, def.anon)
                if (exposed.isEmpty() || exposed.contains(ruleName))
                    globalTable.registerRule(ruleDef)
                else
                    localTable.registerRule(ruleDef)
            }

            val imports = buildFile.import.imports.map { it.text }
            imports.forEach(::resolveInternal)

            fun resolveValue(value: Value, allowNonAnon: Boolean = false): ResolvedValue<Value> =
                    when (value) {
                        is RuleCall -> {
                            val rule = localTable.resolveRule(imports, value.rule.text)
                            if (!rule.anon && !allowNonAnon) throw IllegalRuleCall("'${rule.name}' is not anonymously callable, but is called from ${value.rule.ID.toPos(module)}")
                            ResolvedRuleCall(SourceRef(module, value), rule,
                                    value.params.map { param ->
                                        val name = param.name?.text
                                        val paramDef = if (name == null) rule.params.singleOrNull()
                                                ?: throw InvalidParameterException("'Invalid number of parameters for ${rule.name}' @ ${value.rule.ID.toPos(module)}.")
                                        else rule.params.firstOrNull { it.name == name }
                                                ?: throw InvalidParameterException("'${rule.name}' has no parameter named '${name}' @ ${param.name.toPos(module)}.")
                                        ResolvedRuleParam(SourceRef(module, param), paramDef, resolveValue(param.value))
                                    })
                        }
                        is StringValue -> ResolvedStringValue(SourceRef(module, value), value.value)
                        is RefValue -> ResolvedGoalCall(SourceRef(module, value), localTable.resolveGoal(imports, value.ref.text))
                        is ListValue -> ResolvedListValue(SourceRef(module, value), value.items.map { resolveValue(it) })
                        else -> throw InternalBuildLangError("Unknown value $value")
                    }


            buildFile.declarations.forEach { decl ->
                val resolvedDeclaration = localTable.resolveGoal(emptyList(), decl.name.text)
                resolvedDeclaration.value = resolveValue(decl.value, true)
            }
            buildFile.definitions.forEach { def ->
                val resolvedDefinition = localTable.resolveRule(emptyList(), def.name.text)
                if (def.value != null) {
                    resolvedDefinition.value = resolveValue(def.value, !def.anon)
                } else {
                    resolvedDefinition.nativeRule = ruleRegistry.getRuleClass(resolvedDefinition.name)
                }
            }
        }
        resolveInternal(module)
        return globalTable.exposedGoalsOf(module)
    }
}

class InternalBuildLangError(message: String) : IllegalStateException(message)
class InvalidParameterException(message: String) : RuntimeException(message)
class IllegalRuleCall(message: String) : java.lang.RuntimeException(message)
class RuleNotFoundException(message: String) : java.lang.RuntimeException(message)