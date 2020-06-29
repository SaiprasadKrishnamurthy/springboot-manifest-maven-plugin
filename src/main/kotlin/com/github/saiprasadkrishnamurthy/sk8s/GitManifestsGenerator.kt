package com.github.saiprasadkrishnamurthy.sk8s

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.reflectoring.diffparser.api.model.Diff
import org.apache.commons.io.IOUtils
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


/**
 * GIT manifests generator.
 * @author Sai.
 */
class GitManifestsGenerator {
    companion object Factory {
        fun newInstance(): GitManifestsGenerator = GitManifestsGenerator()
        private val GIT_LOG_ENTRIES_DELIMITER = "|||||_|||||"
    }

    fun generateManifests(generateGitManifestsRequest: GenerateGitManifestsRequest) {
        val checkBranchCommand = "git branch"
        val currBranch = checkBranchCommand.runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                .split("\n").filter { it.startsWith("*") }
                .take(1)[0]
                .replace("*", "")
                .trim()

        val shouldRun = generateGitManifestsRequest.executeOnBranches
                .any { extractVariableNames(currBranch, Pattern.compile(it)).any { it.trim().isNotBlank() } }
        if (shouldRun) {
            val historyCommand = "git --no-pager log --oneline --decorate"
            val logs = historyCommand.runCommand(File(generateGitManifestsRequest.baseDir)).toString().split("\n")
            val sdf = SimpleDateFormat("dd/MM/yyyy")

            var versionMetadata = logs
                    .asSequence()
                    .take(n = generateGitManifestsRequest.maxRevisions)
                    .filter { it.isNotEmpty() }
                    .map { it.split(" ")[0] }
                    .map { sha ->
                        val entries = "git --no-pager show --pretty= --name-status $sha --first-parent"
                                .runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                                .split("\n")
                                .filter { it.isNotEmpty() }

                        val details = "git --no-pager show -s --pretty=\"%an$GIT_LOG_ENTRIES_DELIMITER%at|||||_|||||%cn|||||_|||||%s\" $sha"
                                .runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                                .split(GIT_LOG_ENTRIES_DELIMITER)
                        val author = details[0]
                        val timestamp = details[1].toLong()
                        val authorName = details[2]
                        val message = details[3]

                        val tickets = generateGitManifestsRequest.ticketPatterns.flatMap {
                            extractVariableNames(message, Pattern.compile(it))
                        }.map { it.trim() }.toList()

                        val mavenVersion = pomVersion(sha, generateGitManifestsRequest)

                        val date = Date()
                        date.time = timestamp * 1000
                        VersionMetadata(gitSha = sha,
                                mavenVersion = mavenVersion,
                                timestamp = timestamp,
                                commitMessage = message.replace("\"", "").replace("\n", ""),
                                author = "$author ($authorName)".replace("\"", ""),
                                entries = entries,
                                tickets = tickets.distinct(),
                                day = sdf.format(date))
                    }.filter { it.mavenVersion != "" }
                    .toList()

            if (versionMetadata.isNotEmpty()) {
                val currentVersion = versionMetadata[0].mavenVersion
                versionMetadata = versionMetadata.map { v ->
                    if (v.mavenVersion != currentVersion && v.mavenVersion.contains("snapshot", true)) {
                        v.copy(mavenVersion = v.mavenVersion.replace("-snapshot", "", true))
                    } else {
                        v
                    }
                }
            }
            val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(versionMetadata)
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "versionInfo.json"), json)

            var htmlTemplate = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/index.html"), Charset.defaultCharset())
            var js = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/index.js"), Charset.defaultCharset())
            var css = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/styles.css"), Charset.defaultCharset())
            var html = htmlTemplate.replace("{{json}}", json)
            html = html.replace("{{artifactId}}", generateGitManifestsRequest.artifactId)
            html = html.replace("{{version}}", if (versionMetadata.isNotEmpty()) versionMetadata[0].mavenVersion else "")
            var ddTemplate = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/detailed_diff.js"), Charset.defaultCharset())
            if (generateGitManifestsRequest.maxNoOfMavenVersionsForDiffsDump > 0) {
                val diffs = databaseDump(generateGitManifestsRequest, versionMetadata)
                val diffJsonString = jacksonObjectMapper().writeValueAsString(diffs)
                ddTemplate = ddTemplate.replace("{{diffJson}}", diffJsonString)
                Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "diffs.json"), diffJsonString)
                Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "detailed_diff.js"), ddTemplate)
            } else {
                ddTemplate = ddTemplate.replace("{{diffJson}}", "[]")
                Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "detailed_diff.js"), ddTemplate)
            }
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "index.html"), html, Charset.defaultCharset())
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "index.js"), js, Charset.defaultCharset())
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "styles.css"), css, Charset.defaultCharset())
        } else {
            println("Not generating the GIT manifests as the plugin is not configured to run on the branch:  $currBranch")
        }
    }

    private fun pomVersion(sha: String, generateGitManifestsRequest: GenerateGitManifestsRequest): String {
        return try {
            val pomContents = "git show $sha:pom.xml".runCommand(File(generateGitManifestsRequest.baseDir))
            val xpFactory = XPathFactory.newInstance()
            val xPath = xpFactory.newXPath().compile("//*[local-name() = 'version']")
            val nodeList = xPath.evaluate(InputSource(StringReader(pomContents)), XPathConstants.NODESET) as NodeList
            if (nodeList.length > 0) {
                var node = nodeList.item(0)
                if (node.parentNode.localName == "parent") {
                    node = nodeList.item(1)
                }
                node.textContent
            } else ""
        } catch (ex: Exception) {
            ""
        }
    }

    private fun databaseDump(generateGitManifestsRequest: GenerateGitManifestsRequest, versionMetadata: List<VersionMetadata>): MutableList<DiffLog> {
        val mavenVersions = versionMetadata.map { it.mavenVersion }.distinct()
        println("Found Maven Versions: $mavenVersions")
        val diffs = mutableListOf<DiffLog>()
        if (mavenVersions.size > 1) {
            // N-1 Version diff.
            val mv = mavenVersions.take(generateGitManifestsRequest.maxNoOfMavenVersionsForDiffsDump)
            val traversed = mutableSetOf<String>()

            mv.forEach { a ->
                mv.forEach { b ->
                    if (a != b && !traversed.contains(listOf(a, b).sorted().joinToString(","))) {
                        traversed.add(listOf(a, b).sorted().joinToString(","))
                        val i = versionMetadata.filter { it.mavenVersion == a }
                        val j = versionMetadata.filter { it.mavenVersion == b }
                        val lastGitSha = j[0].gitSha
                        val idx = mavenVersions.indexOf(a)
                        val mavenVersionCanonicalNameA = if (idx == 0) "CURRENT" else "CURRENT-$idx"
                        val mavenVersionCanonicalNameB = "CURRENT-(${idx - 1}"
                        diffs.addAll(diffs(i, j[0].mavenVersion, mavenVersionCanonicalNameA, mavenVersionCanonicalNameB, lastGitSha, generateGitManifestsRequest))
                    }
                }
            }
        }
        return diffs
    }

    private fun diffs(v: List<VersionMetadata>, prevMavenVersion: String, mavenVersionCanonicalNameA: String, mavenVersionCanonicalNameB: String, lastGitSha: String, generateGitManifestsRequest: GenerateGitManifestsRequest): List<DiffLog> {
        return v.flatMap {
            it.entries.map { f ->
                val file = f.split("\\s".toRegex()).filter { it.trim().isNotBlank() }[1]
                val cmd = "git diff ${it.gitSha} $lastGitSha $file"
                val d = cmd.runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                DiffLog(mavenVersionA = it.mavenVersion,
                        mavenVersionB = prevMavenVersion,
                        gitVersionA = it.gitSha,
                        gitVersionB = lastGitSha,
                        file = file,
                        diff = d,
                        author = it.author,
                        timestamp = it.timestamp,
                        commitMessage = it.commitMessage,
                        mavenVersionCanonicalNameA = mavenVersionCanonicalNameA,
                        mavenVersionCanonicalNameB = mavenVersionCanonicalNameB)
            }
        }
    }

    private fun contents(diff: Diff, lineType: String) = diff.latestHunk.lines
            .filter { it.lineType.name == lineType }.joinToString("\n") { it.content }

    private fun extractVariableNames(value: String, regex: Pattern): Set<String> {
        val matchPattern = regex.matcher(value)
        val vars = HashSet<String>()
        while (matchPattern.find()) {
            vars.add(matchPattern.group(0))
        }
        return vars
    }


    fun String.runCommand(
            workingDir: File = File("."),
            timeoutAmount: Long = 60,
            timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ): String? = try {
        ProcessBuilder(split("\\s".toRegex()))
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start().apply { waitFor(timeoutAmount, timeoutUnit) }
                .inputStream.bufferedReader().readText()
    } catch (e: java.io.IOException) {
        e.printStackTrace()
        ""
    }
}