package org.whisk.ext

import org.apache.logging.log4j.LogManager
import java.io.File
import java.net.URL
import java.net.URLClassLoader

class ExtClassLoader : URLClassLoader {
    private val log = LogManager.getLogger()

    constructor(urls: Array<out URL>, parent: ClassLoader?) : super(urls, parent) {
        val sizeOfCP = urls.map { File(it.toURI()).length() }.sum() / 1024 / 1024
        log.debug("Classpath consists of ${urls.size} entries, with an aggregated size of $sizeOfCP MiB and a parent classloader")

    }

    constructor(urls: Array<out URL>) : super(urls) {
        val sizeOfCP = urls.map { File(it.toURI()).length() }.sum() / 1024 / 1024
        log.debug("Classpath consists of ${urls.size} entries, with an aggregated size of $sizeOfCP MiB")
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> =
            synchronized(getClassLoadingLock(name)) {
                if (name.startsWith("org.whisk.ext.bridge"))
                    super.loadClass(name, resolve)
                else if (name.startsWith("org.whisk.ext.impl") || name == "org.whisk.WhiskIOKt") {
                    findLoadedClass(name) ?: kotlin.run {
                        val classContent = getResource(name.replace('.', '/') + ".class")
                                ?.openStream()?.use {
                                    it.readBytes()
                                } ?: error("Could not find class $name")
                        defineClass(name, classContent, 0, classContent.size)
                    }
                } else
                    try {
                        findLoadedClass(name) ?: findClass(name)
                    } catch (e: ClassNotFoundException) {
                        super.loadClass(name, resolve)
                    }
            }
}