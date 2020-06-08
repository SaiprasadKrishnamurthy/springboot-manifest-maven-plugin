package com.github.saiprasadkrishnamurthy.sk8s

import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * K8s manifests generator for a springboot application.
 * @author Sai.
 */
class K8sManifestsGenerator {
    companion object Factory {
        fun newInstance(): K8sManifestsGenerator = K8sManifestsGenerator()
        private val REGEX_EXTRACT_VARIABLE_NAMES_FROM_TEMPLATE = Pattern.compile("\\$\\{(.*?)\\}")
    }

    fun generateManifests(generateK8sManifestsRequest: GenerateK8sManifestsRequest) {

        val propsDir = Paths.get(generateK8sManifestsRequest.baseDir, "src/main/resources")

        val propsContexts = File(propsDir.toString()).walk()
                .filter { it.isFile }
                .filter { it.extension == "properties" }
                .map {
                    val nameWithoutExtension = it.nameWithoutExtension
                    val profile = if (nameWithoutExtension == "application") "_" else nameWithoutExtension.replace("application-", "")
                    val props = Properties()
                    props.load(FileInputStream(it))
                    PropertiesContext(profile = profile, file = it.path, props = props.toMutableMap())
                }.toList()

        val baseProps = propsContexts
                .filter { it.profile == "_" }
                .ifEmpty { listOf(PropertiesContext(profile = "_", file = "", props = mutableMapOf())) }
                .first()
        loadProps(generateK8sManifestsRequest, baseProps)
        propsContexts
                .filter { it.profile != "_" }
                .forEach { loadProps(generateK8sManifestsRequest, it) }

        propsContexts
                .filter { it.profile != "_" }
                .forEach {
                    val b = mutableMapOf<Any, Any>()
                    val n = mutableMapOf<Any, Any>()
                    b.putAll(baseProps.props)
                    b.putAll(it.props)
                    n.putAll(baseProps.normalisedProps)
                    n.putAll(it.normalisedProps)
                    it.props.putAll(b)
                    it.normalisedProps.putAll(n)
                }

        propsContexts.forEach { pc ->
            var deploymentTemplate = Paths.get(generateK8sManifestsRequest.baseDir, generateK8sManifestsRequest.deploymentYmlTemplateFile).toFile().readText(Charset.defaultCharset())
            var configMapTemplate = Paths.get(generateK8sManifestsRequest.baseDir, generateK8sManifestsRequest.configMapYmlTemplateFile).toFile().readText(Charset.defaultCharset())
            pc.props.forEach { (k, v) ->
                deploymentTemplate = deploymentTemplate.replace("\${${k.toString()}}", v.toString())
            }
            pc.normalisedProps.forEach { (k, v) ->
                deploymentTemplate = deploymentTemplate.replace("\${${k.toString()}}", v.toString())
            }
            val profile = if (pc.profile == "_") "" else "_${pc.profile}"
            Files.writeString(Paths.get(generateK8sManifestsRequest.outputDir, "deployment$profile.yml"), deploymentTemplate, Charset.defaultCharset())

            val properties = pc.normalisedProps.map {
                "  ${it.key}: ${it.value}"
            }.joinToString("\n")
            configMapTemplate = configMapTemplate.replace("\${properties}", properties)
            configMapTemplate = configMapTemplate.replace("\${configMapTemplateName}", pc.normalisedProps["configMapTemplateName"].toString())
            Files.writeString(Paths.get(generateK8sManifestsRequest.outputDir, "configMap$profile.yml"), configMapTemplate, Charset.defaultCharset())
        }
    }

    private fun loadProps(generateK8sManifestsRequest: GenerateK8sManifestsRequest, propsContext: PropertiesContext) {
        loadDefaultScopedProps(generateK8sManifestsRequest, propsContext)
        propsContext.props.forEach {
            val baseKey = toEnvironmentVariableFriendlyString(it.key.toString())
            var baseValue = it.value.toString()
            val variableNamesInValues = extractVariableNames(baseValue, REGEX_EXTRACT_VARIABLE_NAMES_FROM_TEMPLATE)
            variableNamesInValues.forEach { v ->
                baseValue = baseValue.replace("\${$v}", "\${${toEnvironmentVariableFriendlyString(v)}}")
            }
            propsContext.normalisedProps[baseKey] = baseValue
        }
    }

    private fun loadDefaultScopedProps(generateK8sManifestsRequest: GenerateK8sManifestsRequest, propsCtx: PropertiesContext) {
        propsCtx.props["artifactId"] = generateK8sManifestsRequest.artifactId
        propsCtx.props["random"] = UUID.randomUUID().toString()
        propsCtx.props["version"] = generateK8sManifestsRequest.version
        propsCtx.props["gitVersion"] = "git rev-parse HEAD".runCommand(File(generateK8sManifestsRequest.baseDir)).toString()
        propsCtx.props["configMapTemplateName"] = "${generateK8sManifestsRequest.artifactId}-${generateK8sManifestsRequest.version}".toLowerCase()
        propsCtx.props["imageName"] = generateK8sManifestsRequest.dockerImageName
        if (!propsCtx.props.containsKey("replicas") && !propsCtx.normalisedProps.containsKey("replicas")) {
            propsCtx.props["replicas"] = "1"
        }
        if (!propsCtx.props.containsKey("minReadySeconds") && !propsCtx.normalisedProps.containsKey("minReadySeconds")) {
            propsCtx.props["minReadySeconds"] = "60"
        }
    }

    private fun toEnvironmentVariableFriendlyString(key: String): String {
        return key.replace(".", "_").replace("-", "")
    }

    private fun extractVariableNames(value: String, regex: Pattern): Set<String> {
        val matchPattern = regex.matcher(value)
        val vars = HashSet<String>()
        while (matchPattern.find()) {
            vars.add(matchPattern.group(1))
        }
        return vars
    }


    private fun String.runCommand(
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