package org.whisk.ext.bridge

interface AntlrTool {
    fun processGrammarsOnCommandLine(args: List<String>): Int

}