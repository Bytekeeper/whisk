package org.whisk.model

import org.apache.logging.log4j.LogManager
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.io.InputStream
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.primaryConstructor

class TomlRuleParser @Inject constructor(private val ruleRegistry: RuleModelRegistry) : RuleParser {
    private val log = LogManager.getLogger()

    override fun parse(source: InputStream): Map<String, RuleModel> {
        val result = Toml.parse(source)

        result.errors().forEach { log.error(it) }

        return result.toMap().flatMap { (k, v) ->
            v as TomlArray

            val ruleClass = ruleRegistry.getRuleClass(k)
            v.asTables().map { mapToRule(ruleClass, it) }
        }.map { it.name to it }.toMap()
    }

    private fun mapToRule(ruleClass: KClass<out RuleModel>, table: TomlTable): RuleModel {
        val primaryConstructor = ruleClass.primaryConstructor
            ?: throw java.lang.IllegalStateException("Invalid rule ${ruleClass.simpleName}, no primary constructor found!")
        val arguments = primaryConstructor.parameters
            .mapNotNull {
                val value = when (it.type.classifier) {
                    String::class -> table.getString(it.name!!)
                    List::class -> mapToList(it.type.arguments.firstOrNull(), it.name!!, table)
                    else -> throw IllegalArgumentException("Invalid rule parameter '${it.name}' with type '${it.type}' for from rule '${ruleClass.simpleName}'!")
                }
                if (value == null) {
                    if (it.isOptional) null
                    else if (it.type.isMarkedNullable) it to null
                    else throw IllegalStateException("Missing value for rule parameter '${it.name}' for from rule '${ruleClass.simpleName}'!")
                } else
                    it to value
            }.toMap()
        return primaryConstructor.callBy(arguments)
    }

    private fun mapToList(
        projection: KTypeProjection?,
        name: String,
        table: TomlTable
    ) =
        when (projection?.type?.classifier) {
            String::class -> table.getArray(name)?.toList()
            else -> throw IllegalArgumentException("Invalid rule parameter '${name}', type '${projection?.type}' is not supported!")
        }


    private fun TomlArray.asTables(): List<TomlTable> {
        val result = mutableListOf<TomlTable>()
        for (i in 0 until size()) {
            result += getTable(i)
        }
        return result
    }
}