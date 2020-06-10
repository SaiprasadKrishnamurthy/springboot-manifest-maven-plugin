package com.github.saiprasadkrishnamurthy.sk8s

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
        private val REGEX_EXTRACT_VARIABLE_NAMES_FROM_TEMPLATE = Pattern.compile("\\$\\{(.*?)\\}")
        private val GIT_LOG_ENTRIES_DELIMITER = "|||||_|||||"
    }

    fun generateManifests(generateGitManifestsRequest: GenerateGitManifestsRequest) {
        val historyCommand = "git log --oneline --graph --decorate --first-parent"
        val logs = historyCommand.runCommand(File(generateGitManifestsRequest.baseDir)).toString().split("\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy")

        val versionMetadata = logs
                .filter { it.isNotEmpty() }
                .map { it.split(" ")[1] }
                .map { sha ->
                    val entries = "git show --pretty= --name-status $sha"
                            .runCommand(File(generateGitManifestsRequest.baseDir)).toString()
                            .split("\n")
                            .filter { it.isNotEmpty() }

                    val details = "git show -s --pretty=\"%an$GIT_LOG_ENTRIES_DELIMITER%at|||||_|||||%cn|||||_|||||%s\" $sha"
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
                            commitMessage = message.replace("\"","").replace("\n", ""),
                            author = "$author ($authorName)".replace("\"",""),
                            entries = entries,
                            tickets = tickets.distinct(),
                            day = sdf.format(date))
                }.filter { it.mavenVersion != "" }
        val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(versionMetadata)
        Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "versionInfo.json"), json)

        var htmlTemplate = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/index.html"), Charset.defaultCharset())
        var js = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/index.js"), Charset.defaultCharset())
        var css = IOUtils.toString(GitManifestsGenerator::class.java.classLoader.getResourceAsStream("templates/styles.css"), Charset.defaultCharset())
        var html = htmlTemplate.replace("{{json}}", json)
        html = html.replace("{{artifactId}}", generateGitManifestsRequest.artifactId)
        Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "index.html"), html, Charset.defaultCharset())
        Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "index.js"), js, Charset.defaultCharset())
        Files.writeString(Paths.get(generateGitManifestsRequest.outputDir, "styles.css"), css, Charset.defaultCharset())
    }

    private fun pomVersion(sha: String, generateGitManifestsRequest: GenerateGitManifestsRequest): String {
        return try {
            val pomContents = "git show $sha:pom.xml".runCommand(File(generateGitManifestsRequest.baseDir))
            val xpFactory = XPathFactory.newInstance()
            val xPath = xpFactory.newXPath().compile("//*[local-name() = 'version']")
            val nodeList = xPath.evaluate(InputSource(StringReader(pomContents)), XPathConstants.NODESET) as NodeList
            if (nodeList.length > 1) nodeList.item(1).textContent else nodeList.item(0).textContent
        } catch (ex: Exception) {
            ""
        }
    }

    private fun extractVariableNames(value: String, regex: Pattern): Set<String> {
        val matchPattern = regex.matcher(value)
        val vars = HashSet<String>()
        while (matchPattern.find()) {
            vars.add(matchPattern.group(1))
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