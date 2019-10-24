package org.whisk.ext.impl

import org.mybatis.generator.api.ConnectionFactory
import org.mybatis.generator.api.MyBatisGenerator
import org.mybatis.generator.config.xml.ConfigurationParser
import org.mybatis.generator.internal.DefaultShellCallback
import org.whisk.ext.bridge.MyBatisGeneratorRunner
import java.io.File
import java.sql.Connection
import java.util.*

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

class WhiskConnectorFactory : ConnectionFactory {
    override fun addConfigurationProperties(properties: Properties?) {
    }

    override fun getConnection(): Connection {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}