package org.whisk.buildlang

import org.antlr.v4.runtime.*
import javax.inject.Inject

data class GoalDeclaration(val name: Token, val value: Value)
data class RuleDefinition(val name: Token, val ruleParamDefs: List<RuleParamDef>, val value: Value?, val anon: Boolean = false)
data class BuildFile(val import: Import, val export: Export, val declarations: List<GoalDeclaration>, val definitions: List<RuleDefinition>)
interface Value
data class RefValue(val ref: BuildLangParser.QNameContext) : Value
data class StringValue(val value: String) : Value
data class ListValue(val items: List<Value> = emptyList()) : Value
data class RuleCall(val rule: BuildLangParser.QNameContext, val params: List<RuleParam>) : Value
data class RuleParam(val name: Token?, val value: Value)
data class RuleParamDef(val name: Token, val type: ParamType, val optional: Boolean)
data class Import(val imports: List<BuildLangParser.QNameContext>)
data class Export(val exports: List<BuildLangParser.QNameContext>)

enum class ParamType {
    STRING,
    LIST
}

fun Token.toPos(module: String) = "Module '$module': $line:${charPositionInLine + 1}"

class BLErrorListener : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
        val additionalInfo =
                if (e is NoViableAltException) {
                    if (e.ctx is BuildLangParser.RuleCallContext && e.offendingToken.type == BuildLangParser.COMMA) {
                        ": Multiparameter rule calls require named parameters: ie. rule(name=value, name2=value2) and not rule(value, value2)"
                    } else ""
                } else ""
        throw ParseError("line $line:$charPositionInLine $msg$additionalInfo")
    }
}

class ParseError(message: String) : Throwable(message)

class BuildLangTransformer @Inject constructor() {
    fun buildFileFrom(ctx: BuildLangParser.BuildFileContext): BuildFile =
            BuildFile(
                    Import(ctx.imports()?.packages ?: emptyList()),
                    Export(ctx.exports()?.rules ?: emptyList()),
                    ctx.declarations.map(::declarationFrom),
                    ctx.definitions.map(::definitionFrom))

    private fun declarationFrom(ctx: BuildLangParser.DeclarationContext): GoalDeclaration =
            GoalDeclaration(ctx.name, ValueVisitor.visitChildren(ctx))

    private fun definitionFrom(ctx: BuildLangParser.DefinitionContext): RuleDefinition {
        val ruleParamDefs = ctx.params.map(::ruleParamDefFrom)
        return RuleDefinition(ctx.name, ruleParamDefs, ValueVisitor.visitChildren(ctx), ctx.ANON() != null)
    }

    private fun ruleParamDefFrom(ctx: BuildLangParser.RuleParamDefContext) =
            RuleParamDef(ctx.name, if (ctx.type != null) ParamType.LIST else ParamType.STRING, ctx.optional != null || ctx.type != null)
}

private object ValueVisitor : BuildLangParserBaseVisitor<Value>() {
    override fun visitListItem(ctx: BuildLangParser.ListItemContext): Value {
        if (ctx.qName() != null) return RefValue(ctx.qName())
        return super.visitListItem(ctx)
    }

    override fun visitList(ctx: BuildLangParser.ListContext): Value {
        return ListValue(ctx.items.map { visit(it) ?: error("$it could not be handled") })
    }

    override fun visitString(ctx: BuildLangParser.StringContext): Value {
        return StringValue(ctx.value)
    }

    override fun visitRuleCall(ctx: BuildLangParser.RuleCallContext): Value {
        val ruleParams =
                if (ctx.param != null) listOf(RuleParam(null, visitChildren(ctx.param)))
                else ctx.params.map { RuleParam(it.name, visitChildren(it)) }

        return RuleCall(ctx.name, ruleParams)
    }
}
