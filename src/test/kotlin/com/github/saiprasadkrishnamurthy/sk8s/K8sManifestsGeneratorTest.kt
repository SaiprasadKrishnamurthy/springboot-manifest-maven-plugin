package com.github.saiprasadkrishnamurthy.sk8s

import org.junit.jupiter.api.BeforeAll
import java.nio.file.Paths

class K8sManifestsGeneratorTest {

    companion object {
        @BeforeAll
        @JvmStatic
        internal fun setBaseDir() {
            System.setProperty("user.dir", ".")
        }
    }

    @org.junit.jupiter.api.Test
    fun generateManifests() {
        val req = GenerateK8sManifestsRequest(artifactId = "demo-service",
                version = "1.0-SNAPSHOT",
                dockerImageName = "saiprasadkrishnamurthy/demo-service",
                deploymentYmlTemplateFilesDir = "deployment",
                configMapYmlTemplateFile = "deployment/configmap-template.yml",
                outputDir = "target")
        Paths.get(req.outputDir, req.artifactId).toFile().mkdirs()
        K8sManifestsGenerator.newInstance().generateManifests(req)

    }
}