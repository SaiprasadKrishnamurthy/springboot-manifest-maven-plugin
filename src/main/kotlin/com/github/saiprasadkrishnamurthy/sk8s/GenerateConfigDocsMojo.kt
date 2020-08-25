package com.github.saiprasadkrishnamurthy.sk8s

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import java.nio.file.Paths

/**
 * An example Maven Mojo that generates SpringBoot config docs Spring boot properties files.
 * @author Sai Kris.
 */
@Mojo(name = "generate-config-docs", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
class GenerateConfigDocsMojo : AbstractMojo() {

    @Parameter(property = "project")
    private lateinit var project: MavenProject

    @Parameter(property = "skip")
    private var skip: Boolean = false

    @Parameter(property = "outputDir", defaultValue = "target/manifests/k8s")
    private lateinit var outputDir: String

    @Parameter(property = "configFileType")
    private var configFileType: String = "yml"

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        if (skip) {
            log.warn(" GenerateConfigDocsMojo disabled ")
        } else {
            try {
                val groupId = project.groupId
                val artifactId = project.artifactId
                val version = project.version
                Paths.get(outputDir, artifactId).toFile().mkdirs()
                log.info(String.format(" Generating Config Docs for:  %s:%s:%s", groupId, artifactId, version))
                if (configFileType == "yml" || configFileType == "yaml") {
                    YmlPropertiesDocumentationGenerator.newInstance().generateDoc(GeneratePropsDocRequest(artifactId = artifactId,
                            version = version,
                            baseDir = project.basedir.absolutePath,
                            outputDir = Paths.get(outputDir, artifactId).toString()))
                } else {
                    log.error("Unsupported Config Files format!")
                }
            } catch (ex: Exception) {
                log.error(ex)
                throw RuntimeException(ex)
            }
        }
    }
}