package com.github.saiprasadkrishnamurthy.sk8s

import org.junit.jupiter.api.BeforeAll

class GitManifestsGeneratorTest {

    companion object {
        @BeforeAll
        @JvmStatic
        internal fun setBaseDir() {
            System.setProperty("user.dir", ".")
        }
    }

    @org.junit.jupiter.api.Test
    fun generateManifests() {
        val req = GenerateGitManifestsRequest(artifactId = "event-relationships-enrichment-service",
                outputDir = "target")
        GitManifestsGenerator.newInstance().generateManifests(req)

    }
}