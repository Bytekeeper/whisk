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
import org.whisk.forkJoinTask
import org.whisk.model.FileResource
import org.whisk.model.MavenLibrary
import java.io.PrintWriter
import java.net.URI
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
        session.setReadOnly()
    }

    override fun execute(
            execution: Execution<MavenLibrary>
    ): RuleResult {
        val rule = execution.ruleParameters
        val depFile = Paths.get("${execution.goalName}.mvn")
        var verifyFiles = false

        if (!depFile.toFile().exists()) {
            verifyFiles = true
            val configuredRepos = rule.repository_urls.mapIndexed { i, item ->
                RemoteRepository.Builder("repo$i", "default", item.string).build()
            }
            val additionalRepos = if (configuredRepos.isEmpty())
                listOf(RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build())
            else
                emptyList<RemoteRepository>()
            val repos = configuredRepos + additionalRepos
            val repositoryLayouts = repos.map { it to repositoryLayoutProvider.newRepositoryLayout(session, it) }

            log.info("Resolving maven dependencies for ${execution.goalName}")

            val collectRequest = CollectRequest(
                    rule.artifacts
                            .map { DefaultArtifact(it.string) }
                            .map { Dependency(it, "") },
                    null, repos
            )
            val result = system.collectDependencies(session, collectRequest)
            val listGenerator = PreorderNodeListGenerator()
            result.root.accept(listGenerator)
            val artifacts = listGenerator.nodes
                    .filter { !it.dependency.optional && it.dependency.scope in arrayOf("", "compile") }
                    .map { it.artifact }
                    .sortedBy { it.toString() }

            PrintWriter(Files.newBufferedWriter(depFile, StandardCharsets.UTF_8))
                    .use { out ->
                        artifacts.forEach { a ->
                            val urls = repositoryLayouts.map { (repo, layout) -> URI(repo.url).resolve(layout.getLocation(a, false)) }
                                    .joinToString(",")
                            out.println(urls)
                        }
                    }
        } else {
            log.debug("Dependency file for ${execution.goalName} exists, using it...")
        }
        val forwardDeps = Files.newBufferedReader(depFile)
                .useLines {
                    it.map { url ->
                        forkJoinTask {
                            download(
                                    execution.cacheDir,
                                    url.split(',').map { URL(it) }
                            )
                        }.fork()
                    }.toList()
                }.map { FileResource(it.join().toAbsolutePath(), source = rule) }.toList()
        return Success(forwardDeps)
    }

}

class MavenDependencyChanged(message: String) : Exception(message)
