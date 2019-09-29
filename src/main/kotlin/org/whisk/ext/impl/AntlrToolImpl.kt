package org.whisk.ext.impl

import org.antlr.v4.Tool
import org.whisk.ext.bridge.AntlrTool

class AntlrToolImpl : AntlrTool {
    override fun processGrammarsOnCommandLine(args: List<String>): Int {
        val tool = Tool(args.toTypedArray())
        tool.processGrammarsOnCommandLine()
        return tool.numErrors
    }
}