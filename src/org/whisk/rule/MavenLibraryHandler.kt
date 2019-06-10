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
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator
import org.tomlj.Toml
import org.whisk.model.MavenLibrary
import org.whisk.sha1
import org.whisk.toHex
import java.io.PrintWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

class MavenLibraryHandler @Inject constructor() :
    RuleHandler<MavenLibrary> {
    private val repositoryLayoutProvider: RepositoryLayoutProvider
    private val log = LogManager.getLogger()
    private val session: DefaultRepositorySystemSession
    private val system: RepositorySystem

    init {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        system = locator.getService(RepositorySystem::class.java)
        repositoryLayoutProvider = locator.getService(RepositoryLayoutProvider::class.java)
        session = MavenRepositorySystemUtils.newSession()
        session.localRepositoryManager = system.newLocalRepositoryManager(session,
            LocalRepository("whisk-out/m2repo")
        )
    }

    override fun build(
        execution: Execution<MavenLibrary>
    ): RuleResult {
        val rule = execution.rule
        val depFile = Paths.get("${rule.name}-dep.toml")
        var verifyFiles = false

        if (!depFile.toFile().exists()) {
            verifyFiles = true
            val remoteRepository = RemoteRepository.Builder(
                "central", "default",
                rule.repositoryUrl ?: "https://repo.maven.apache.org/maven2/"
            ).build()
            val repositoryLayout = repositoryLayoutProvider.newRepositoryLayout(session, remoteRepository)

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
            val repositoryUrl = remoteRepository.url
            val artifacts = listGenerator.nodes.map { it.artifact }.sortedBy { it.toString() }
            PrintWriter(Files.newBufferedWriter(depFile, StandardCharsets.UTF_8))
                .use { out ->
                    out.println("root_artifacts=${rule.artifacts.sorted().joinToString("\", \"", "[\"", "\"]")}")
                    artifacts.forEach { a ->
                        val downloadPath = repositoryLayout.getLocation(a, false)
                        val downloadURL = repositoryUrl + downloadPath
                        val expectedSHA1 = repositoryLayout.getChecksums(a, false, downloadPath)
                            .firstOrNull { it.algorithm == "SHA-1" }
                            ?.let {checksum ->
                                URL(repositoryUrl + checksum.location).openStream()
                                    .use { String(it.readBytes(), 0, 40) }
                            } ?: ""
                        out.println("[[maven_artifact]]")
                        out.println("name=\"${a.groupId}_${a.artifactId}\"")
                        out.println("url=\"$downloadURL\"")
                        out.println("sha1=\"$expectedSHA1\"")
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
                    execution.cacheDir,
                    URL(artifact.getString("url") ?: throw IllegalStateException("URL missing"))
                )
            if (verifyFiles) {
                val expectedSha1 = artifact.getString("sha1")
                val actualSha1 = download.sha1().toHex()
                if (expectedSha1 != actualSha1) {
                    throw InvalidChecksumError("Maven artifact ${artifact.getString("name")} of rule ${rule.name} has an invalid checksum, expected $expectedSha1, but got $actualSha1!")
                }
            }
            forwardDeps += download.toString()

        }
        return RuleResult(rule, forwardDeps)
    }

}

class MavenDependencyChanged(message: String) : Exception(message)
