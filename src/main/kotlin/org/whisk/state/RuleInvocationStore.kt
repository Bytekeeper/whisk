package org.whisk.state

import com.google.protobuf.InvalidProtocolBufferException
import org.whisk.model.FileResource
import org.whisk.model.Resource
import org.whisk.model.RuleParameters
import org.whisk.model.StringResource
import org.whisk.proto.LastState
import org.whisk.rule.ExecutionContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.reflect.full.memberProperties

class RuleInvocationStore @Inject constructor() {
    fun <T> readLastState(path: Path, reader: (ByteArray) -> T): T? =
            if (path.toFile().exists()) {
                try {
                    reader(Files.readAllBytes(path))
                } catch (e: InvalidProtocolBufferException) {
                    null
                }
            } else null

    fun readLastInvocation(executionContext: ExecutionContext<out RuleParameters>) = readLastInvocation(toLastCallPath(executionContext))

    private fun toLastCallPath(executionContext: ExecutionContext<out RuleParameters>) =
            executionContext.targetPath.resolve(executionContext.ruleRef.source.rule.text + ".lastcall")

    fun readLastInvocation(path: Path): LastState.Invocation? = readLastState(path, LastState.Invocation::parseFrom)


    fun writeNewInvocation(path: Path,
                           ruleParameters: RuleParameters,
                           result: List<Resource>,
                           messages: List<String>): LastState.Invocation? =
            writeNewInvocation(path, ruleParameters.toStorageFormat(), result, messages)

    fun writeNewInvocation(
            executionContext: ExecutionContext<out RuleParameters>,
            ruleCall: LastState.RuleCall,
            result: List<Resource> = emptyList(),
            messages: List<String> = emptyList()) =
            writeNewInvocation(toLastCallPath(executionContext), ruleCall, result, messages)

    fun writeNewInvocation(
            path: Path,
            ruleCall: LastState.RuleCall,
            result: List<Resource>,
            messages: List<String>): LastState.Invocation? {
        val invocationBuilder = LastState.Invocation.newBuilder()
        invocationBuilder.ruleCall = ruleCall
        invocationBuilder.addAllMessage(messages)
        result.forEach {
            resourceToStorageFormat(it, invocationBuilder.addResultBuilder())
        }
        val invocation = invocationBuilder.build()
        Files.write(path, invocation.toByteArray())
        return invocation
    }

}

fun RuleParameters.toStorageFormat() = toParameterMap().toRuleCall()
private fun RuleParameters.toParameterMap(): Map<String, List<Resource>> {
    val params = javaClass.kotlin.memberProperties
            .map {
                val value = it.get(this)
                it.name to (if (value is Resource) listOf(value) else value as? List<Resource> ?: emptyList())
            }.toMap()
    return params
}

private fun Map<String, List<Resource>>.toRuleCall(): LastState.RuleCall {
    val ruleCallBuilder = entries
            .fold(LastState.RuleCall.newBuilder()) { ruleCall, (name, resources) ->
                resources.fold(ruleCall.addRuleParamsBuilder().setName(name)) { ruleParam, res ->
                    val riBuilder = ruleParam.addResourceInfoBuilder()
                    resourceToStorageFormat(res, riBuilder)

                    ruleParam
                }
                ruleCall
            }
    return ruleCallBuilder.build()
}


fun List<LastState.ResourceInfo>.toResources(source: RuleParameters) = map { it.storageFormatToResource(source) }

private fun resourceToStorageFormat(res: Resource, riBuilder: LastState.ResourceInfo.Builder) {
    when (res) {
        is FileResource -> riBuilder.fileBuilder
                .setName(res.path.toString())
                .setRoot(res.root.toString())
                .setPlaceHolder(res.placeHolder?.toString() ?: "")
                .setLength(Files.size(res.path))
                .setTimestamp(Files.getLastModifiedTime(res.path).toMillis())
        is StringResource -> riBuilder.stringBuilder
                .setString(res.string)
                .setDefiningModule(res.definingModule)
    }
}

private fun LastState.ResourceInfo.storageFormatToResource(source: RuleParameters) =
        when (kindCase) {
            LastState.ResourceInfo.KindCase.FILE -> FileResource(
                    Paths.get(file.name),
                    Paths.get(file.root),
                    source,
                    if (file.placeHolder.isNotEmpty()) Paths.get(file.placeHolder) else null)
            LastState.ResourceInfo.KindCase.STRING -> StringResource(string.string, source, string.definingModule)
            else -> error("Unknown resource type ${kindCase}")
        }