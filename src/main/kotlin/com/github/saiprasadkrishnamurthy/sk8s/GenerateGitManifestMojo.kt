package com.github.saiprasadkrishnamurthy.sk8s

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.io.File

/**
 * An example Maven Mojo that generates GIT manifests.
 * @author Sai Kris.
 */
@Mojo(name = "generate-git-manifests", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
class GenerateGitManifestsMojo : AbstractMojo() {

    @Parameter(property = "project")
    private lateinit var project: MavenProject

    @Parameter(property = "skip")
    private var skip: Boolean = false

    @Parameter(property = "outputDir", defaultValue = "target/manifests/git")
    private lateinit var outputDir: String

    @Parameter(property = "ticketPatterns", defaultValue = "")
    private lateinit var ticketPatterns: String

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skip) {
            log.warn(" GenerateGitManifestsMojo disabled ")
        } else {
            try {
                val groupId = project.groupId
                val artifactId = project.artifactId
                val version = project.version
                File(outputDir).mkdirs()
                log.info(String.format(" Generating GIT Manifest Files for:  %s:%s:%s", groupId, artifactId, version))
                GitManifestsGenerator.newInstance().generateManifests(GenerateGitManifestsRequest(
                        ticketPatterns = ticketPatterns.split(","),
                        outputDir = outputDir,
                        artifactId = project.artifactId))
            } catch (ex: Exception) {
                log.error(ex)
                throw RuntimeException(ex)
            }
        }
    }
}