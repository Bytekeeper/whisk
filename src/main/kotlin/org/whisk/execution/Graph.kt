package org.whisk.execution

import org.whisk.buildlang.*
import javax.inject.Inject


data class Node(var goal: ResolvedGoal, val dependencies: List<Node>)
class Graph(val nodes: List<Node>)

class GraphBuilder @Inject constructor() {
    fun buildFrom(availableGoals: List<ResolvedGoal>, goal: String): Graph {
        val entryGoal = availableGoals.singleOrNull { it.name == goal }
                ?: throw IllegalArgumentException("No such goal $goal, valid goals are ${availableGoals.joinToString { it.name }}. Did you 'expose' the goal?")
        val graphVisitor = GraphVisitor()

        graphVisitor.visitGoal(entryGoal)

        return Graph(graphVisitor.symbolToNode.values.toList())
    }

    class GraphVisitor {
        val symbolToNode = mutableMapOf<Any, Node>()
        private val visited = mutableSetOf<Any>()

        fun visitGoal(goal: ResolvedGoal): Node {
            var node = symbolToNode[goal]
            if (node != null) return node
            if (visited.contains(goal)) throw CyclicDependenciesException("${goal.name} has cyclic dependencies")
            visited += goal

            val dependencies = goal.value?.let { visitValue(it) } ?: emptyList()
            node = Node(goal, dependencies)
            symbolToNode[goal] = node
            return node
        }

        private fun visitValue(value: ResolvedValue<Value>): List<Node> =
                when (value) {
                    is ResolvedRuleCall -> (value.rule.value?.let { visitValue(it) }
                            ?: emptyList()) + value.params.flatMap { visitValue(it.value) }
                    is ResolvedStringValue -> emptyList()
                    is ResolvedGoalCall -> listOf(visitGoal(value.goal))
                    is ResolvedListValue -> value.items.flatMap { visitValue(it) }
                    is ResolvedRuleParamValue -> emptyList()
                    else -> throw IllegalStateException("Unknown value $value")
                }
    }
}


class CyclicDependenciesException(message: String) : RuntimeException(message)