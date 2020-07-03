package com.github.saiprasadkrishnamurthy.sk8s

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.nio.file.Paths

/**
 * An example Maven Mojo that generates GIT manifests.
 * @author Sai Kris.
 */
@Mojo(name = "generate-git-manifests", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
class GenerateGitManifestsMojo : AbstractMojo() {

    @Parameter(property = "project")
    private lateinit var project: MavenProject

    @Parameter(property = "skip")
    private var skip: Boolean = false

    @Parameter(property = "outputDir", defaultValue = "target/manifests/git")
    private lateinit var outputDir: String

    @Parameter(property = "ticketPatterns", defaultValue = "ZZAABBCCXEWQX")
    private lateinit var ticketPatterns: String

    @Parameter(property = "maxNoOfMavenVersionsForDiffsDump", defaultValue = "0")
    private lateinit var maxNoOfMavenVersionsForDiffsDump: String

    @Parameter(property = "maxRevisions", defaultValue = "100")
    private lateinit var maxRevisions: String

    @Parameter(property = "runOnBranchPatterns", defaultValue = "master")
    private lateinit var runOnBranchPatterns: String

    @Parameter(property = "transitiveDepsDatabaseDump", defaultValue = "false")
    private lateinit var transitiveDepsDatabaseDump: String

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skip) {
            log.warn(" GenerateGitManifestsMojo disabled ")
        } else {
            try {
                val groupId = project.groupId
                val artifactId = project.artifactId
                val version = project.version
                Paths.get(outputDir, artifactId).toFile().mkdirs()
                log.info(String.format(" Generating GIT Manifest Files for:  %s:%s:%s", groupId, artifactId, version))
                GitManifestsGenerator.newInstance().generateManifests(GenerateGitManifestsRequest(
                        ticketPatterns = ticketPatterns.split(","),
                        outputDir = outputDir,
                        artifactId = project.artifactId,
                        maxRevisions = maxRevisions.toInt(),
                        maxNoOfMavenVersionsForDiffsDump = maxNoOfMavenVersionsForDiffsDump.toInt(),
                        executeOnBranches = runOnBranchPatterns.split(","),
                        dependencyArtifacts = project.artifacts.toList(),
                        transitiveDepsDatabaseDump = transitiveDepsDatabaseDump.toBoolean(),
                        project = project
                ))
            } catch (ex: Exception) {
                ex.printStackTrace()
                log.error(ex)
                throw RuntimeException(ex)
            }
        }
    }
}