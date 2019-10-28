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
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.whisk.BuildProperties
import org.whisk.DownloadManager
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
import javax.inject.Inject

class MavenLibraryHandler @Inject constructor(
        private val downloadManager: DownloadManager,
        private val buildProperties: BuildProperties
) :
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

    override val name: String = "Maven Libraries Download"

    override fun execute(
            execution: ExecutionContext<MavenLibrary>
    ): RuleResult {
        val rule = execution.ruleParameters
        val depFile = execution.targetPath.resolve("${execution.goalName}.mvn")
        var verifyFiles = false

        if (!depFile.toFile().exists()) {
            log.info("Resolving maven dependencies for ${execution.goalName}")
            verifyFiles = true
            val configuredRepos = rule.repository_urls.mapIndexed { i, item ->
                val repositoryBuilder = RemoteRepository.Builder("repo$i", "default", if (item.string.endsWith('/')) item.string else item.string + '/')
                val url = URL(item.string)
                val username = buildProperties.username(url.host)
                val password = buildProperties.password(url.host)
                if (username != null && password != null) {
                    repositoryBuilder.setAuthentication(
                            AuthenticationBuilder()
                                    .addUsername(username)
                                    .addPassword(password)
                                    .build())
                }
                repositoryBuilder.build()
            }
            val additionalRepos = if (configuredRepos.isEmpty())
                listOf(RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build())
            else
                emptyList<RemoteRepository>()
            val repos = configuredRepos + additionalRepos
            val repositoryLayouts = repos.map { it to repositoryLayoutProvider.newRepositoryLayout(session, it) }

            val collectRequest = CollectRequest(
                    rule.artifacts
                            .map { DefaultArtifact(it.string) }
                            .map { Dependency(it, "") },
                    null, repos
            )
            val result = system.collectDependencies(session, collectRequest)
            val listGenerator = PreorderNodeListGenerator()
            result.root.accept(listGenerator)
            val scopes = if (rule.scopes.isEmpty()) listOf("", "compile", "runtime") else rule.scopes.map { it.string }
            val artifacts = listGenerator.nodes
                    .filter { !it.dependency.optional && it.dependency.scope in scopes }
                    .map { it.artifact }
                    .sortedBy { it.toString() }

            Files.createDirectories(execution.targetPath)
            PrintWriter(Files.newBufferedWriter(depFile, StandardCharsets.UTF_8))
                    .use { out ->
                        artifacts.forEach { a ->
                            val urls = repositoryLayouts.map { (repo, layout) ->
                                URI(repo.url).resolve(layout.getLocation(a, false))
                            }
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
                            downloadManager.download(
                                    execution.cacheDir,
                                    url.split(',').map { URL(it) }
                            )
                        }.fork()
                    }.toList()
                }.map { FileResource(it.join().toAbsolutePath(), execution.cacheDir.toAbsolutePath(), rule) }.toList()
        return Success(forwardDeps)
    }

}

class MavenDependencyChanged(message: String) : Exception(message)
