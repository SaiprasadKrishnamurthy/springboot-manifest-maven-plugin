package com.github.saiprasadkrishnamurthy.sk8s

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.reflectoring.diffparser.api.model.Diff
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SystemUtils
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.zeroturnaround.exec.ProcessExecutor
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
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
        // Turn off paging.
        "git config --global core.pager cat".runCommand(File(generateGitManifestsRequest.baseDir))
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

            val multimoduleProject = generateGitManifestsRequest.project.modules.isNotEmpty()
            var versionMetadata = logs
                    .asSequence()
                    .take(n = generateGitManifestsRequest.maxRevisions)
                    .filter { it.isNotEmpty() }
                    .map { it.split(" ")[0] }
                    .map { sha ->
                        var entries = "git --no-pager show --pretty= --name-status $sha --first-parent"
                                .runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                                .split("\n")
                                .filter { it.isNotEmpty() }

                        if (multimoduleProject)
                            entries = entries.filter { it.contains("${generateGitManifestsRequest.artifactId}/") }


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
                                day = sdf.format(date),
                                artifactId = generateGitManifestsRequest.artifactId)
                    }.filter { it.mavenVersion != "" }
                    .filter { it.entries.isNotEmpty() }
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
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "versionInfo.json"), json)

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
                Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "diffs.json"), diffJsonString)
                Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "detailed_diff.js"), ddTemplate)
            } else {
                ddTemplate = ddTemplate.replace("{{diffJson}}", "[]")
                Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "detailed_diff.js"), ddTemplate)
            }
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "index.html"), html, Charset.defaultCharset())
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "index.js"), js, Charset.defaultCharset())
            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "styles.css"), css, Charset.defaultCharset())

            val deps = generateGitManifestsRequest.dependencyArtifacts.map {
                DependenciesInfo(parentArtifactId = generateGitManifestsRequest.artifactId,
                        parentVersion = generateGitManifestsRequest.project.version,
                        dependencyArtifactId = it.artifactId,
                        dependencyVersion = it.version,
                        name = generateGitManifestsRequest.project.name,
                        description = generateGitManifestsRequest.project.description,
                        url = generateGitManifestsRequest.project.url
                )
            }

            Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, generateGitManifestsRequest.artifactId, "dependencies.json"), jacksonObjectMapper().writeValueAsString(deps), Charset.defaultCharset())

            if (generateGitManifestsRequest.transitiveDepsDatabaseDump) {
                val releaseDB = generateGitManifestsRequest.outputDir + "/" + generateGitManifestsRequest.artifactId + ".db"
                val connection = DriverManager.getConnection("jdbc:sqlite:$releaseDB")
                val statement = connection.createStatement()
                statement.executeUpdate("create table VERSION_INFO (gitSha string, artifactId, mavenVersion string, timestamp long, author string, commitMessage string, tickets text, entries text, day string)")
                statement.executeUpdate("CREATE INDEX artifactId ON VERSION_INFO (artifactId)")
                statement.executeUpdate("CREATE INDEX mavenVersion ON VERSION_INFO (mavenVersion)")
                statement.executeUpdate("CREATE INDEX author ON VERSION_INFO (author)")
                statement.executeUpdate("CREATE INDEX gitSha ON VERSION_INFO (gitSha)")
                statement.queryTimeout = 30
                val pstmt = connection.prepareStatement(
                        "INSERT INTO VERSION_INFO(gitSha, artifactId, mavenVersion, timestamp, author, commitMessage, tickets, entries, day) VALUES(?,?,?,?,?,?,?,?,?)")
                try {
                    generateGitManifestsRequest.dependencyArtifacts.forEach { a ->
                        val jarFile = JarFile(a.file)
                        val entries = jarFile.entries()
                        while (entries.hasMoreElements()) {
                            val element = entries.nextElement()
                            if (element.name.endsWith("versionInfo.json")) {
                                val istream = jarFile.getInputStream(element)
                                val contents = IOUtils.toString(istream, Charset.defaultCharset())
                                val vi = jacksonObjectMapper().readValue(contents.toByteArray(Charset.defaultCharset()), object : TypeReference<List<VersionMetadata>>() {})
                                loadToDB(vi, pstmt, a.artifactId)
                            }
                        }
                    }
                    loadToDB(versionMetadata, pstmt, generateGitManifestsRequest.artifactId)
                } finally {
                    pstmt.close()
                    statement.close()
                    connection.close()
                }
            }
        } else {
            println("Not generating the GIT manifests as the plugin is not configured to run on the branch:  $currBranch")
        }
    }

    private fun loadToDB(versionMetadata: List<VersionMetadata>, pstmt: PreparedStatement, artifactId: String) {
        versionMetadata.forEach { vm ->
            pstmt.setString(1, vm.gitSha)
            pstmt.setString(2, artifactId)
            pstmt.setString(3, vm.mavenVersion)
            pstmt.setLong(4, vm.timestamp)
            pstmt.setString(5, vm.author)
            pstmt.setString(6, vm.commitMessage)
            pstmt.setString(7, vm.tickets.joinToString(","))
            pstmt.setString(8, vm.entries.joinToString(","))
            pstmt.setString(9, vm.day)
            // Add row to the batch.
            pstmt.addBatch();
        }
        pstmt.executeBatch()
    }

    private fun pomVersion(sha: String, generateGitManifestsRequest: GenerateGitManifestsRequest): String {
        return try {
            val pom = if (SystemUtils.OS_NAME.toLowerCase().contains("windows")) "pom.xml" else "./pom.xml"
            val pomContents = "git --no-pager show $sha:$pom".runCommand(File(generateGitManifestsRequest.baseDir))
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
                        diffs.addAll(diffs(i, j[0].mavenVersion, mavenVersionCanonicalNameB, mavenVersionCanonicalNameA, lastGitSha, generateGitManifestsRequest))
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
                val cmd = "git diff $lastGitSha ${it.gitSha} $file"
                val d = cmd.runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                DiffLog(mavenVersionA = it.mavenVersion,
                        mavenVersionB = prevMavenVersion,
                        gitVersionA = lastGitSha,
                        gitVersionB = it.gitSha,
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
        ProcessExecutor()
                .command(split("\\s".toRegex()))
                .readOutput(true)
                .directory(workingDir)
                .start()
                .future
                .get()
                .outputUTF8()
    } catch (e: java.io.IOException) {
        e.printStackTrace()
        ""
    }
}