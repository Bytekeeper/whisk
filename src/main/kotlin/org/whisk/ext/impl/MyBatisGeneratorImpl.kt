package org.whisk.ext.impl

import org.mybatis.generator.api.MyBatisGenerator
import org.mybatis.generator.config.xml.ConfigurationParser
import org.mybatis.generator.internal.DefaultShellCallback
import org.whisk.ext.bridge.MyBatisGeneratorRunner
import java.io.File

class MyBatisGeneratorImpl : MyBatisGeneratorRunner {
    override fun process(configFile: File) {
        val warnings = mutableListOf<String>()
        val cp = ConfigurationParser(warnings);
        val config = cp.parseConfiguration(configFile);
        val callback = DefaultShellCallback(true);
        val myBatisGenerator = MyBatisGenerator(config, callback, warnings);
        myBatisGenerator.generate(null);
    }
}