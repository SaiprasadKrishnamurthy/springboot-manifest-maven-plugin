package com.github.saiprasadkrishnamurthy.sk8s

import org.apache.maven.artifact.Artifact
import org.apache.maven.project.MavenProject

/**
 * Various model objects.
 * @author Sai.
 */

data class PropertiesContext(val profile: String, val props: MutableMap<Any, Any> = mutableMapOf(), val normalisedProps: MutableMap<Any, Any> = mutableMapOf(), val file: String)

data class GenerateK8sManifestsRequest(val artifactId: String,
                                       val version: String,
                                       val dockerImageName: String,
                                       val deploymentYmlTemplateFile: String,
                                       val configMapYmlTemplateFile: String,
                                       val outputDir: String,
                                       val baseDir: String = System.getProperty("user.dir"))

data class GenerateGitManifestsRequest(val outputDir: String,
                                       val artifactId: String,
                                       val baseDir: String = System.getProperty("user.dir"),
                                       val ticketPatterns: List<String> = listOf(),
                                       val maxRevisions: Int = 100,
                                       val maxNoOfMavenVersionsForDiffsDump: Int = 0,
                                       val executeOnBranches: List<String> = listOf("master"),
                                       val dependencyArtifacts: List<Artifact> = emptyList(),
                                       val transitiveDepsDatabaseDump: Boolean = false,
                                       val project: MavenProject)

data class VersionMetadata(val gitSha: String,
                           val mavenVersion: String,
                           val timestamp: Long,
                           val author: String,
                           val commitMessage: String,
                           val tickets: List<String>,
                           val entries: List<String>,
                           val day: String,
                           val artifactId: String)

data class DiffLog(val mavenVersionA: String,
                   val mavenVersionB: String,
                   val gitVersionA: String,
                   val gitVersionB: String,
                   val author: String,
                   val timestamp: Long,
                   val file: String,
                   val diff: String,
                   val commitMessage: String,
                   val mavenVersionCanonicalNameA: String,
                   val mavenVersionCanonicalNameB: String)

data class DependenciesInfo(val parentArtifactId: String,
                            val parentVersion: String,
                            val dependencyArtifactId: String,
                            val dependencyVersion: String,
                            val name: String,
                            val description: String,
                            val url: String)