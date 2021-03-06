package com.github.saiprasadkrishnamurthy.sk8s

import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
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

        val propsContexts1 = loadProps(propsDir)
        val propsContexts2 = loadYml(propsDir)
        val propsContexts = propsContexts1 + propsContexts2

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
            var configMapTemplate = File(generateK8sManifestsRequest.configMapYmlTemplateFile).readText(Charset.defaultCharset())
            val profile = if (pc.profile == "_") "" else "_${pc.profile}"
            val properties = pc.normalisedProps.map {
                "  ${it.key}: ${it.value}"
            }.joinToString("\n")
            configMapTemplate = configMapTemplate.replace("\${properties}", properties)
            configMapTemplate = configMapTemplate.replace("\${configMapTemplateName}", pc.normalisedProps["configMapTemplateName"].toString())
            Files.writeString(Paths.get(generateK8sManifestsRequest.outputDir, generateK8sManifestsRequest.artifactId, "configMap$profile.yml"), configMapTemplate, Charset.defaultCharset())
        }

        propsContexts.forEach { pc ->
            Files.list(Paths.get(generateK8sManifestsRequest.deploymentYmlTemplateFilesDir))
                    .filter { !it.toFile().name.contains("configmap") }
                    .filter { it.toFile().isFile }
                    .filter { it.toFile().extension == "yml" || it.toFile().extension == "yaml" }
                    .forEach {
                        var deploymentTemplate = it.toFile().readText(Charset.defaultCharset())
                        pc.props.forEach { (k, v) ->
                            deploymentTemplate = deploymentTemplate.replace("\${${k.toString()}}", v.toString())
                        }
                        pc.normalisedProps.forEach { (k, v) ->
                            deploymentTemplate = deploymentTemplate.replace("\${${k.toString()}}", v.toString())
                        }
                        val profile = if (pc.profile == "_") "" else "_${pc.profile}"
                        val fileName = it.toFile().nameWithoutExtension.replace("-template", "").replace("template", "").replace("Template", "")
                        Files.writeString(Paths.get(generateK8sManifestsRequest.outputDir, generateK8sManifestsRequest.artifactId, "$fileName$profile.yml"), deploymentTemplate, Charset.defaultCharset())
                    }
        }
    }

    private fun loadProps(propsDir: Path): List<PropertiesContext> {
        return File(propsDir.toString()).walk()
                .filter { it.isFile }
                .filter { it.extension == "properties" }
                .map {
                    val nameWithoutExtension = it.nameWithoutExtension
                    val profile = if (nameWithoutExtension == "application") "_" else nameWithoutExtension.replace("application-", "")
                    val props = Properties()
                    props.load(FileInputStream(it))
                    PropertiesContext(profile = profile, file = it.path, props = props.toMutableMap())
                }.toList()
    }

    private fun loadYml(propsDir: Path): List<PropertiesContext> {
        return File(propsDir.toString()).walk()
                .filter { it.isFile }
                .filter { it.extension == "yml" || it.extension == "yaml" }
                .filter { !it.name.contains("-template.yml") }
                .map {
                    val nameWithoutExtension = it.nameWithoutExtension
                    val profile = if (nameWithoutExtension == "application") "_" else nameWithoutExtension.replace("application-", "")
                    val ymlToProps = YamlConverter.ymlToProps(it.absolutePath)
                    PropertiesContext(profile = profile, file = it.path, props = ymlToProps)
                }.toList()
    }

    private fun loadProps(generateK8sManifestsRequest: GenerateK8sManifestsRequest, propsContext: PropertiesContext) {
        loadDefaultScopedProps(generateK8sManifestsRequest, propsContext)
        propsContext.props.forEach {
            val baseKey = toEnvironmentVariableFriendlyString(it.key.toString())
            var baseValue = "\"${it.value}\"".replace("\n", "")
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