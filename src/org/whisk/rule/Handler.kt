package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.tomlj.Toml
import org.whisk.kotlin.KotlinCompiler
import org.whisk.model.*
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.streams.toList

data class RuleResult(
    val fromRule: RuleModel,
    val files: List<String>
)

data class DependencyReferences(val refs: Map<Any, List<String>>)

interface RuleHandler<T : RuleModel> {
    fun build(rule: T, ruleInput: RuleInput): RuleResult
    fun dependencyReferences(rule: T): DependencyReferences =
        DependencyReferences(mapOf("DEPS" to rule.deps))
}

private fun download(whiskDir: Path, url: URL): Path {
    val log = LogManager.getLogger()
    val targetFile = whiskDir.resolve(url.path.substring(1))
    if (targetFile.toFile().exists()) {
        log.debug("{} exists, not downloading...", targetFile)
    } else {
        log.info("Downloading {}...", targetFile)
        Files.createDirectories(targetFile.parent)
        url.openStream().use { content ->
            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return targetFile
}

class PrebuiltJarHandler @Inject constructor() : RuleHandler<PrebuiltJar> {
    override fun build(rule: PrebuiltJar, ruleInput: RuleInput): RuleResult {
        if (!File(rule.binary_jar).exists()) throw java.lang.IllegalStateException("${rule.name} file does not exist!")
        return RuleResult(rule, listOf(rule.binary_jar))
    }

}

class RemoteFileHandler @Inject constructor() : RuleHandler<RemoteFile> {
    private val log = LogManager.getLogger()

    override fun build(rule: RemoteFile, ruleInput: RuleInput): RuleResult {
        val whiskDir = Paths.get(".whisk")
        val url = URL(rule.url)
        val targetFile = download(whiskDir, url)
        return RuleResult(rule, listOf(targetFile.toAbsolutePath().toString()))
    }

}

class JavaBinaryHandler @Inject constructor() : RuleHandler<JavaBinary> {
    private val log = LogManager.getLogger()

    override fun build(rule: JavaBinary, ruleInput: RuleInput): RuleResult {
        val whiskOut = Paths.get("whisk-out")
        val jarDir = whiskOut.resolve("jar")

        val jarName = jarDir.resolve("${rule.name}.jar")
        val deps = ruleInput.allResults()

        JarOutputStream(Files.newOutputStream(jarName))
            .use { out ->
                val usedNames = mutableSetOf<String>()
                rule.mainClass?.let { mainClass ->
                    out.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
                    val writer = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
                    writer.print("Main-Class: ")
                    writer.println(mainClass)
                    writer.flush()
                    usedNames += "META-INF/"
                    usedNames += "META-INF/MANIFEST.MF"
                }

                deps.forEach {
                    it.files.forEach { file ->
                        if (!usedNames.contains(file)) {
                            if (file.endsWith(".jar")) {
                                JarInputStream(Files.newInputStream(Paths.get(file)))
                                    .use { jar ->
                                        var entry = jar.nextJarEntry
                                        while (entry != null) {
                                            if (!usedNames.contains(entry.name)) {
                                                out.putNextEntry(JarEntry(entry.name))
                                                jar.copyTo(out)
                                                usedNames += entry.name
                                            } else {
                                                log.warn("Duplicate file $file:${entry.name}")
                                            }
                                            entry = jar.nextJarEntry
                                        }
                                    }
                            } else {
                                out.putNextEntry(JarEntry(file))
                                Files.copy(Paths.get(file), out)
                            }
                            usedNames += file
                        } else {
                            log.warn("Duplicate file $file")
                        }
                    }
                }
            }
        return RuleResult(rule, listOf(jarName.toString()))
    }

}

class KotlinCompileHandler @Inject constructor(private val kotlinCompiler: KotlinCompiler) :
    RuleHandler<KotlinCompile> {
    private val DEPS = "deps"
    private val KAPT_DEPS = "kapt_deps"
    private val EXPORTED_DEPS = "exported_deps"
    private val PROVIDED_DEPS = "provided_deps"

    override fun build(rule: KotlinCompile, ruleInput: RuleInput): RuleResult {
        val whiskOut = Paths.get("whisk-out")
        val classesDir = whiskOut.resolve("classes")
        val jarDir = whiskOut.resolve("jar")
//
        val matcher = org.whisk.PathMatcher.toRegex(rule.srcs[0])
        val base = Paths.get(".")
        val srcs = Files.walk(base)
            .use {
                it.map { base.relativize(it) }
                    .filter {
                        matcher.matches(it.toString())
                    }.toList()
            }

        val deps = ruleInput.results[DEPS] ?: emptyList()
        val exportedDeps: List<RuleResult> = ruleInput.results[EXPORTED_DEPS] ?: emptyList()
        val providedDeps: List<RuleResult> = ruleInput.results[PROVIDED_DEPS] ?: emptyList()
        val dependencies = (deps + exportedDeps + providedDeps).flatMap { it.files }
        val kapt = (ruleInput.results[KAPT_DEPS] ?: emptyList()).flatMap { it.files }
        val kaptClasspath = dependencies//.filter { !it.contains("kotlin-compiler") }
        val params = srcs.map { it.toString() }
        kotlinCompiler.compile(params, dependencies, kaptClasspath, kapt, classesDir.toString())

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${rule.name}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
            .use { out ->
                Files.walk(classesDir).use { pathStream ->
                    pathStream
                        .forEach { path ->
                            val relativePath = classesDir.relativize(path)
                            if (Files.isRegularFile(path)) {
                                out.putNextEntry(JarEntry(relativePath.toString()));
                                Files.copy(path, out)
                            } else if (Files.isDirectory(path)) {
                                out.putNextEntry(JarEntry("$relativePath/"))
                            }
                        }
                }
            }

        val exportedFiles = exportedDeps.flatMap { it.files }
        return RuleResult(rule, exportedFiles + jarName.toString())
    }

    override fun dependencyReferences(rule: KotlinCompile): DependencyReferences =
        DependencyReferences(
            mapOf(
                DEPS to (rule.deps - rule.exported_deps),
                KAPT_DEPS to rule.kapt_deps,
                EXPORTED_DEPS to rule.exported_deps,
                PROVIDED_DEPS to rule.provided_deps
            )
        )
}

class KotlinTestHandler @Inject constructor(private val kotlinCompiler: KotlinCompiler) : RuleHandler<KotlinTest> {
    override fun build(rule: KotlinTest, ruleInput: RuleInput): RuleResult {
//        val compiler = Kotlin()
        val whiskOut = Paths.get("whisk-out")
        val classesDir = whiskOut.resolve("test-classes")
        val jarDir = whiskOut.resolve("jar")

        val matcher = org.whisk.PathMatcher.toRegex(rule.srcs[0])
        val base = Paths.get(".")
        val srcs = Files.walk(base)
            .use {
                it.map { base.relativize(it) }
                    .filter {
                        matcher.matches(it.toString())
                    }.toList()
            }

//        val arguments = Params(
//            classpath = ruleInput.flatMap { it.files }.map { File(it) },
//            srcs = srcs.map { it.toString() },
//            apClasspath = ruleInput.flatMap { it.files }.filter { it.contains("dagger-compiler") }
//        )
//
        val dependencies = ruleInput.allResults().flatMap { it.files }
        val params = srcs.map { it.toString() }

        kotlinCompiler.compile(params, dependencies, emptyList(), emptyList(), classesDir.toString())

        val cl = URLClassLoader(((dependencies.map {
            File(it).toURI().toURL()
        } + classesDir.toUri().toURL()).toTypedArray()))

        val classes = Files.walk(classesDir).use { path ->
            path.filter { Files.isRegularFile(it) && it.endsWith(".class") && !it.toString().contains("$")}
                .map { path ->
                    val rel = classesDir.relativize(path)
                    cl.loadClass(
                        rel.toString()
                            .replace('/', '.')
                            .replace(".class", "")
                    )
                }.toList().toTypedArray()
        }

        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TextListener(System.out))
        val result = jUnitCore.run(*classes)

        return RuleResult(rule, emptyList())
    }
}

class MavenLibraryHandler @Inject constructor() : RuleHandler<MavenLibrary> {
    private val log = LogManager.getLogger()
    private val session: DefaultRepositorySystemSession
    private val system: RepositorySystem

    init {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        system = locator.getService(RepositorySystem::class.java)
        session = MavenRepositorySystemUtils.newSession()
        session.localRepositoryManager = system.newLocalRepositoryManager(session, LocalRepository("out/m2repo"))
    }

    override fun build(rule: MavenLibrary, ruleInput: RuleInput): RuleResult {
        val depFile = Paths.get("${rule.name}-dep.toml")

        if (!depFile.toFile().exists()) {
            val remoteRepository = RemoteRepository.Builder(
                "central", "default",
                rule.repositoryUrl ?: "https://repo.maven.apache.org/maven2/"
            ).build()

            log.info("Resolving maven dependencies for ${rule.name}")
            log.warn("TODO: DO NOT DOWNLOAD WHILE BUILDING! A separate command should be done")
            val collectRequest = CollectRequest(
                rule.artifacts
                    .map { DefaultArtifact(it) }
                    .map { Dependency(it, "") },
                null, listOf(remoteRepository)
            )
            val result = system.collectDependencies(session, collectRequest)
            val listGenerator = PreorderNodeListGenerator()
            result.root.accept(listGenerator)
            val artifacts = listGenerator.nodes.map { it.artifact }.sortedBy { it.toString() }
            PrintWriter(Files.newBufferedWriter(depFile, StandardCharsets.UTF_8))
                .use { out ->
                    out.println("root_artifacts=${rule.artifacts.sorted().joinToString("\", \"", "[\"", "\"]")}")
                    artifacts.forEach { a ->
                        out.println("[[maven_artifact]]")
                        out.println("name=\"${a.groupId}_${a.artifactId}\"")
                        // http://central.maven.org/maven2/org/apache/maven/resolver/maven-resolver-connector-basic/1.3.3/maven-resolver-connector-basic-1.3.3.jar
                        val groupPath = a.groupId.replace('.', '/')
                        out.println("url=\"http://central.maven.org/maven2/$groupPath/${a.artifactId}/${a.version}/${a.artifactId}-${a.version}.jar\"")
                        out.println("sha1=\"\"")
                        out.println()
                    }
                }
        } else {
            log.debug("Dependency file for ${rule.name} exists, using it...")
        }
        val depFileTable = Toml.parse(depFile)
        if (depFileTable.getArray("root_artifacts")?.toList() != rule.artifacts.sorted())
            throw MavenDependencyChanged("Maven artifact list of ${rule.name} has changed, delete '$depFile' and refetch!")
        val mavenArtifactsList = depFileTable.getArray("maven_artifact")
            ?: throw IllegalStateException("Invalid dependency file: $depFile")
        val forwardDeps = mutableListOf<String>()
        for (i in 0 until mavenArtifactsList.size()) {
            val artifact = mavenArtifactsList.getTable(i)
            val download =
                download(
                    Paths.get(".whisk"),
                    URL(artifact.getString("url") ?: throw IllegalStateException("URL missing"))
                )
            forwardDeps += download.toString()

        }
        return RuleResult(rule, forwardDeps)
    }

    class MavenDependencyChanged(message: String) : Exception(message)
}
