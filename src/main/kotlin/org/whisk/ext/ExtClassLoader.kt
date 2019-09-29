package org.whisk.ext

import java.net.URL
import java.net.URLClassLoader

class ExtClassLoader : URLClassLoader {
    constructor(urls: Array<out URL>, parent: ClassLoader?) : super(urls, parent)

    constructor(urls: Array<out URL>) : super(urls)

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