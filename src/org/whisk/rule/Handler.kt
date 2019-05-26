package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
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
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.classpathAsList
import org.tomlj.Toml
import org.whisk.model.*
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import kotlin.streams.toList

data class RuleResult(
    val rule: Rule,
    val files: List<String>
)

interface RuleHandler<T : Rule> {
    fun process(rule: T, deps: List<RuleResult>): RuleResult
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

object RemoteFileHandler : RuleHandler<RemoteFile> {
    private val log = LogManager.getLogger()

    override fun process(rule: RemoteFile, deps: List<RuleResult>): RuleResult {
        val whiskDir = Paths.get(".whisk")
        val url = URL(rule.url)
        val targetFile = download(whiskDir, url)
        return RuleResult(rule, listOf(targetFile.toAbsolutePath().toString()))
    }

}

object JavaBinaryHandler : RuleHandler<JavaBinary> {
    private val log = LogManager.getLogger()

    override fun process(rule: JavaBinary, deps: List<RuleResult>): RuleResult {
        val whiskOut = Paths.get("whisk-out")
        val jarDir = whiskOut.resolve("jar")

        val jarName = jarDir.resolve("${rule.name}.jar")

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

object KotlinLibraryHandler : RuleHandler<KotlinLibrary> {
    private val compiler = K2JVMCompiler()

    override fun process(rule: KotlinLibrary, deps: List<RuleResult>): RuleResult {
        val whiskOut = Paths.get("whisk-out")
        val classesDir = whiskOut.resolve("classes")
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

        val arguments = with(K2JVMCompilerArguments()) {
            noStdlib = true
            classpathAsList = deps.flatMap { it.files }.map { File(it) }
            destination = classesDir.toString()
            freeArgs = srcs.map { it.toString() }
            jvmTarget = "1.8"
            this
        }

        compiler.exec(
            PrintingMessageCollector(System.err, MessageRenderer.WITHOUT_PATHS, false),
            Services.EMPTY,
            arguments
        )

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

        val exportedDeps = deps.filter { (':' + it.rule.name) in rule.exported_deps }.flatMap { it.files }
        return RuleResult(rule, exportedDeps + jarName.toString())
    }
}

object MavenLibraryHandler : RuleHandler<MavenLibrary> {
    private val log = LogManager.getLogger()
    private val session: RepositorySystemSession
    private val system: RepositorySystem
    private val remoteRepository: RemoteRepository

    init {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        system = locator.getService(RepositorySystem::class.java)
        session = MavenRepositorySystemUtils.newSession()
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, LocalRepository("out/m2repo")))
        remoteRepository =
            RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()
    }

    override fun process(rule: MavenLibrary, deps: List<RuleResult>): RuleResult {
        val depFile = Paths.get("${rule.name}.dep")

        if (!depFile.toFile().exists()) {
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
        val mavenArtifactsList = Toml.parse(depFile).getArray("maven_artifact")
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

}