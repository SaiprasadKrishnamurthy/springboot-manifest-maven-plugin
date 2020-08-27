package com.github.saiprasadkrishnamurthy.sk8s

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.nio.file.Files
import java.nio.file.Paths

/**
 * An example Maven Mojo that generates Kubernetes config map files from a hierarchy of Spring boot properties files.
 * @author Sai Kris.
 */
@Mojo(name = "generate-k8s-manifests", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
class GenerateK8sManifestsMojo : AbstractMojo() {

    @Parameter(property = "project")
    private lateinit var project: MavenProject

    @Parameter(property = "dockerImageNamespace")
    private lateinit var dockerImageNamespace: String

    @Parameter(property = "skip")
    private var skip: Boolean = false

    @Parameter(property = "deploymentYmlTemplateFilesDir")
    private lateinit var deploymentYmlTemplateFilesDir: String

    @Parameter(property = "outputDir", defaultValue = "target/manifests/k8s")
    private lateinit var outputDir: String

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skip) {
            log.warn(" GenerateK8sManifestsMojo disabled ")
        } else {
            try {
                val groupId = project.groupId
                val artifactId = project.artifactId
                val version = project.version
                val dockerFullyQualifiedName = "$dockerImageNamespace/$artifactId"
                Paths.get(outputDir, artifactId).toFile().mkdirs()
                log.info(String.format(" Generating Kubernetes Deployment Files for:  %s:%s:%s", groupId, artifactId, version))
                val configMapFileTemplate = Files.list(Paths.get(deploymentYmlTemplateFilesDir)).filter { it.toFile().name.toLowerCase().contains("configmap") }.findFirst().get()
                K8sManifestsGenerator.newInstance().generateManifests(GenerateK8sManifestsRequest(artifactId = artifactId,
                        dockerImageName = dockerFullyQualifiedName,
                        version = version,
                        configMapYmlTemplateFile = configMapFileTemplate.toString(),
                        outputDir = outputDir,
                        baseDir = project.basedir.absolutePath,
                        deploymentYmlTemplateFilesDir = deploymentYmlTemplateFilesDir))
            } catch (ex: Exception) {
                log.error(ex)
                throw RuntimeException(ex)
            }
        }
    }
}