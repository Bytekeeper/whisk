package org.whisk.model

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.nio.file.Path

object TomlRuleParser {
    fun parse(file: Path): Map<String, Rule> {
        val result = Toml.parse(file)

        result.errors().forEach { System.err.println(it) }

        return result.toMap().flatMap { (k, v) ->
            v as TomlArray
            val t = v.asTables()
            when (k) {
                "prebuilt_jar" -> parsePrebuiltJars(v)
                "kotlin_library" -> parseKotlinLibrary(v)
                "remote_file" -> t.map { parseRemoteFile(it) }
                "java_binary" -> t.map { parseJavaBinary(it) }
                "maven_library" -> t.map { parseMavenLibrary(it)}
                else -> throw IllegalArgumentException("Unsupported model: $k")
            }
        }.map { it.name to it }.toMap()
    }

    private fun parseMavenLibrary(mvnLib: TomlTable): Rule {
        val name = mvnLib.getString("name") ?: throw IllegalStateException("Missing name")
        val artifacts = mvnLib.getArray("artifacts")?.toList()?.map { it as String }
            ?: throw IllegalArgumentException("artifacts missing")
        return MavenLibrary(name, artifacts)
    }

    private fun parseJavaBinary(javaBinary: TomlTable): JavaBinary {
        val name = javaBinary.getString("name") ?: throw IllegalStateException("Missing name")
        val deps = javaBinary.getArray("deps")?.toList()?.map { it as String }
            ?: throw IllegalArgumentException("deps missing")
        val mainClass = javaBinary.getString("main_class")
        return JavaBinary(name, deps, mainClass)
    }

    private fun parseRemoteFile(remoteFile: TomlTable): RemoteFile {
        val name = remoteFile.getString("name") ?: throw IllegalStateException("Missing name")
        val url = remoteFile.getString("url") ?: throw IllegalStateException("Missing name")
        val sha1 = remoteFile.getString("sha1") ?: throw IllegalStateException("Missing name")
        return RemoteFile(name, url, sha1)
    }

    private fun parseKotlinLibrary(kotlinLibraries: TomlArray): List<KotlinLibrary> {
        val result = mutableListOf<KotlinLibrary>()
        for (i in 0 until kotlinLibraries.size()) {
            val library = kotlinLibraries.getTable(i)
            val name = library.getString("name") ?: throw IllegalArgumentException("Missing name")
            val srcs = library.getArray("srcs")?.toList()?.map { it as String } ?:  listOf("src/**/*.kt")
            val deps = library.getArray("deps")?.toList()?.map { it as String }
                ?: throw IllegalArgumentException("deps missing")
            val exportedDeps = library.getArray("exported_deps")?.toList()?.map { it as String } ?: emptyList()
            result += KotlinLibrary(name, srcs, deps, exportedDeps)
        }
        return result
    }

    private fun parsePrebuiltJars(prebuiltJars: TomlArray): List<PrebuiltJar> {
        val result = mutableListOf<PrebuiltJar>()
        for (i in 0 until prebuiltJars.size()) {
            val definition = prebuiltJars.getTable(i)
            val name = definition.getString("name") ?: throw IllegalArgumentException("Missing name")
            val binary_jar = definition.getString("binary_jar") ?: throw IllegalArgumentException("binary_jar missing")
            result += PrebuiltJar(name, binary_jar)
        }
        return result
    }

    private fun TomlArray.asTables(): List<TomlTable> {
        val result = mutableListOf<TomlTable>()
        for (i in 0 until size()) {
            result += getTable(i)
        }
        return result
    }
}