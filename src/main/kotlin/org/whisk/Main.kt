package org.whisk

import org.apache.logging.log4j.LogManager
import org.whisk.buildlang.PathModuleLoader
import java.nio.file.Paths


fun main(vararg args: String) {
    val log = LogManager.getLogger()
    val application = DaggerApplication.create()
    val processor = application.processor()
    val graphBuilder = application.graphBuilder()

    val resolvedGoals = application.resolver().resolve(PathModuleLoader(Paths.get("")), "")
    val graph = graphBuilder.buildFrom(resolvedGoals, args[0])
    println(graph)
    println(graph.nodes.filter { it.dependencies.isEmpty() })
}