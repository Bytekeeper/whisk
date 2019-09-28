package org.whisk

import java.net.URL
import java.net.URLClassLoader

class ParentLastClassLoader : URLClassLoader {
    constructor(urls: Array<out URL>, parent: ClassLoader?) : super(urls, parent)

    constructor(urls: Array<out URL>) : super(urls)

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            return try {
                findLoadedClass(name) ?: findClass(name)
            } catch (e: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}