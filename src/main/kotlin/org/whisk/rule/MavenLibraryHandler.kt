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
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.MavenLibrary
import java.io.PrintWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject

class MavenLibraryHandler @Inject constructor() :
        RuleExecutor<MavenLibrary> {
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

    override fun execute(
            execution: Execution<MavenLibrary>
    ): RuleResult {
        val rule = execution.ruleParameters
        val depFile = Paths.get("${execution.goalName}.mvn")
        var verifyFiles = false

        if (!depFile.toFile().exists()) {
            verifyFiles = true
            val remoteRepository = RemoteRepository.Builder(
                    "central", "default",
                    rule.repository_url?.string ?: "https://repo.maven.apache.org/maven2/"
            ).build()
            val repositoryLayout = repositoryLayoutProvider.newRepositoryLayout(session, remoteRepository)

            log.info("Resolving maven dependencies for ${execution.goalName}")
            log.warn("TODO: DO NOT DOWNLOAD WHILE BUILDING! A separate command should be done")
            val collectRequest = CollectRequest(
                    rule.artifacts
                            .map { DefaultArtifact(it.string) }
                            .map { Dependency(it, "") },
                    null, listOf(remoteRepository)
            )
            val listGenerator = synchronized(system) {
                val result = system.collectDependencies(session, collectRequest)
                val listGenerator = PreorderNodeListGenerator()
                result.root.accept(listGenerator)
                listGenerator
            }
            val repositoryUrl = remoteRepository.url
            val artifacts = listGenerator.nodes.map { it.artifact }.sortedBy { it.toString() }
            PrintWriter(Files.newBufferedWriter(depFile, StandardCharsets.UTF_8))
                    .use { out ->
                        artifacts.forEach { a ->
                            val downloadPath = repositoryLayout.getLocation(a, false)
                            val downloadURL = repositoryUrl + downloadPath
                            out.println(downloadURL)
                        }
                    }
        } else {
            log.debug("Dependency file for ${execution.goalName} exists, using it...")
        }
        val forwardDeps = Files.newBufferedReader(depFile)
                .useLines {
                    it.map { url ->
                        download(
                                execution.cacheDir,
                                URL(url)
                        )
                    }.map { FileResource(it.toAbsolutePath(), source = rule) }.toList()
                }
        return Success(forwardDeps)
    }

}

class MavenDependencyChanged(message: String) : Exception(message)
