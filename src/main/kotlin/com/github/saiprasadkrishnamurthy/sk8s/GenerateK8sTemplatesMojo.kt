package com.github.saiprasadkrishnamurthy.sk8s

import org.apache.commons.io.IOUtils
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

/**
 * An example Maven Mojo that generates Kubernetes config map templates files.
 * @author Sai Kris.
 */
@Mojo(name = "generate-k8s-templates", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
class GenerateK8sTemplatesMojo : AbstractMojo() {

    @Parameter(property = "outputDir", defaultValue = "target/manifests/k8s")
    private lateinit var outputDir: String

    @Throws(MojoExecutionException::class, MojoFailureException::class)
    override fun execute() {
        val sd = IOUtils.toString(GenerateK8sTemplatesMojo::class.java.classLoader.getResourceAsStream("templates/service-deployment-template.yml"), Charset.defaultCharset())
        val cm = IOUtils.toString(GenerateK8sTemplatesMojo::class.java.classLoader.getResourceAsStream("templates/configmap-template.yml"), Charset.defaultCharset())
        Files.writeString(Paths.get(outputDir, "service-deployment-template.yml"), sd)
        Files.writeString(Paths.get(outputDir, "configmap-template.yml"), cm)
    }
}