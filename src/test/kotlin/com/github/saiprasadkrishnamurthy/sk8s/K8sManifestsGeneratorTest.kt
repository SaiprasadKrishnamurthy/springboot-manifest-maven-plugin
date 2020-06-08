package com.github.saiprasadkrishnamurthy.sk8s

import org.junit.jupiter.api.BeforeAll

class K8sManifestsGeneratorTest {

    companion object {
        @BeforeAll
        @JvmStatic
        internal fun setBaseDir() {
            System.setProperty("user.dir", "/Users/saiprasadkrishnamurthy/devops/demo-service")
        }
    }

    @org.junit.jupiter.api.Test
    fun generateManifests() {
        val req = GenerateK8sManifestsRequest(artifactId = "demo-service",
                version = "1.0-SNAPSHOT",
                dockerImageName = "saiprasadkrishnamurthy/demo-service",
                deploymentYmlTemplateFile = "deployment/service-deployment-template.yml",
                configMapYmlTemplateFile = "deployment/configmap-template.yml",
                outputDir = "target")
        K8sManifestsGenerator.newInstance().generateManifests(req)

    }
}