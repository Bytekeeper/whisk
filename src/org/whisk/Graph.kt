package org.whisk

import org.whisk.model.RuleModel
import org.whisk.model.RuleParser
import org.whisk.rule.Processor
import java.nio.file.Path
import javax.inject.Inject

data class RuleFQN(val module: String, val ruleName: String) {
    override fun toString(): String = "$module:$ruleName"

    companion object {
        fun fromRef(currentModule: String, reference: String) =
            if (reference[0] == ':') RuleFQN(currentModule, reference.substring(1)) else RuleFQN(
                reference.substringBefore(':'),
                reference.substringAfter(':')
            )
    }
}

class Node(
    val rule: RuleFQN,
    val ruleModel: RuleModel,
    val children: MutableMap<Any, MutableList<Node>> = mutableMapOf()
) {
    override fun toString(): String = "$rule -> $ruleModel"
}

class Graph @Inject constructor(
    private val ruleParser: RuleParser,
    private val processor: Processor
) {
    fun load(projectRoot: Path): Map<RuleFQN, Node> {
        val knownRoots = mutableMapOf<RuleFQN, Node>()
        load(knownRoots, projectRoot, "/")
        return knownRoots.toMap()
    }

    private fun load(knownRules: MutableMap<RuleFQN, Node>, projectRoot: Path, modulePath: String) {
        val rules = ruleParser.parse(projectRoot.resolve(modulePath.substring(1)).resolve("WHISK.toml"))
        rules.mapNotNull { (ruleName, model) ->
            val rule = RuleFQN(modulePath, ruleName)
            if (knownRules.containsKey(rule))
                null
            else {
                val node = Node(rule, model)
                knownRules[rule] = node
                node
            }
        }.forEach { node ->
            processor.retrieveDependencyReferences(node.ruleModel).refs
                .forEach { (group, deps) ->
                    deps.forEach { dep ->
                        val referencedRule = RuleFQN.fromRef(modulePath, dep)
                        if (!knownRules.contains(referencedRule))
                            load(knownRules, projectRoot, referencedRule.module)
                        val dependency = knownRules[referencedRule]
                            ?: throw MissingRule("Could not find '$referencedRule', referenced from ${node.rule}")
                        node.children.computeIfAbsent(group) { mutableListOf() } += dependency
                    }
                }
        }

    }
}

class MissingRule(message: String) : Exception(message)