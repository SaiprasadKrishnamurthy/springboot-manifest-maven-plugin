package com.github.saiprasadkrishnamurthy.sk8s

import com.mitchellbosecke.pebble.PebbleEngine
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.StringWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


/**
 * Yml properties documentation generator.
 * @author Sai.
 */
class YmlPropertiesDocumentationGenerator {

    var propInfos = mutableListOf<YmlPropertyInfo>()

    companion object Factory {
        fun newInstance(): YmlPropertiesDocumentationGenerator = YmlPropertiesDocumentationGenerator()
    }

    fun generateDoc(generatePropsDocRequest: GeneratePropsDocRequest) {
        loadProps(generatePropsDocRequest)
        val leaves = propInfos.filter { it.value != null && it.value.isNotEmpty() }
        val propDesc = leaves.map { ymlPropertyInfo ->
            val hierarchy = hierarchy(ymlPropertyInfo)
            hierarchy.reverse()
            hierarchy.add(ymlPropertyInfo)
            val key = hierarchy.joinToString(".") { it.key }
            PropDescriptor(key = key,
                    values = ymlPropertyInfo.values ?: "",
                    description = ymlPropertyInfo.description ?: "",
                    defaultValue = ymlPropertyInfo.defaultValue ?: "",
                    dataType = ymlPropertyInfo.dataType ?: "",
                    value = ymlPropertyInfo.value,
                    tags = ymlPropertyInfo.tags ?: "",
                    profile = ymlPropertyInfo.profile)
        }
        val groupedByKey = propDesc.groupBy { it.key }

        val vals = groupedByKey.mapValues { v ->
            val pd = v.value
            pd.reduce { a, b ->
                a.copy(
                        values = b.values + " " + a.values,
                        description = b.description + " " + a.description,
                        defaultValue = b.defaultValue + " " + a.defaultValue,
                        dataType = b.dataType + " " + a.dataType,
                        tags = b.tags + " " + a.tags)
            }
        }

        val engine = PebbleEngine.Builder().build()
        val compiledTemplate = engine.getLiteralTemplate(IOUtils.toString(YmlPropertiesDocumentationGenerator.javaClass.classLoader.getResourceAsStream("templates/configDocs.html"), Charset.defaultCharset()))

        val context = HashMap<String, Any>()
        context["props"] = vals.values
        context["artifactId"] = generatePropsDocRequest.artifactId
        context["version"] = generatePropsDocRequest.version
        context["totalProperties"] = groupedByKey.size
        context["totalDocumentedProperties"] = groupedByKey
                .mapValues {
                    if (it.value.any { p -> p.description != null && p.description.isNotEmpty() }) {
                        1
                    } else {
                        0
                    }
                }.map { it.value }
                .sum()
        context["totalUndocumentedProperties"] = groupedByKey.size - context["totalDocumentedProperties"] as Int
        val p = (((context["totalUndocumentedProperties"] as Int).toDouble()) / ((context["totalProperties"] as Int).toDouble())) * 100
        val percentage = String.format("%.1f", p)
        context["percentage"] = percentage

        val writer = StringWriter()
        compiledTemplate.evaluate(writer, context)
        val output = writer.toString()
        Files.writeString(Paths.get(generatePropsDocRequest.outputDir, "configDocs.html"), output)
    }

    private fun hierarchy(leaf: YmlPropertyInfo): MutableList<YmlPropertyInfo> {
        val hierarchy = mutableListOf<YmlPropertyInfo>()
        var parent: YmlPropertyInfo? = leaf
        do {
            parent = parent(parent)
            if (parent != null)
                hierarchy.add(parent)
        } while (parent != null)
        return hierarchy
    }

    private fun parent(ymlPropertyInfo: YmlPropertyInfo?): YmlPropertyInfo? {
        return if (ymlPropertyInfo != null) {
            val sequence = ymlPropertyInfo.sequence
            val level = ymlPropertyInfo.level
            val filtered = propInfos.filter { it.level < level && it.sequence <= sequence }
            if (filtered.isNotEmpty()) filtered[filtered.size - 1] else null
        } else {
            null
        }

    }

    private fun loadProps(generatePropsDocRequest: GeneratePropsDocRequest) {
        var seq = 0
        File("${generatePropsDocRequest.baseDir}/src/main/resources").listFiles()
                .filter { it.name.endsWith("yml") || it.name.endsWith("yaml") }
                .map { file ->
                    val lines = file.readLines(Charset.defaultCharset())
                    lines
                            .filter { it.isNotBlank() }
                            .forEach { line ->
                                val level = line.indexOf(line.trim())
                                val split = line.split(":")
                                val key = split[0].trim()
                                val value = if (split.size > 1) split[1].trim() else null

                                val profile = if (file.nameWithoutExtension == "application") "default" else file.nameWithoutExtension.replace("application-", "")
                                if (line.contains("#doc")) {
                                    val prop = line.substring(line.lastIndexOf("#doc"), line.length - 1)
                                    val contents = prop.substring(5, prop.length)
                                    val map = contents.split("|").associate {
                                        val (left, right) = it.split(":")
                                        left to right.toString()
                                    }
                                    propInfos.add(YmlPropertyInfo(key = key,
                                            value = if (value != null) if (value.contains("#doc")) value.substringBefore("#doc(").trim() else value.trim() else null,
                                            level = level,
                                            description = map["description"],
                                            dataType = map["type"],
                                            values = map["values"],
                                            defaultValue = map["default"],
                                            tags = map["tags"],
                                            sequence = ++seq,
                                            profile = profile))
                                } else {
                                    propInfos.add(YmlPropertyInfo(key = key,
                                            value = if (value != null) if (value.contains("#doc")) value.substringBefore("#doc(") else value.trim() else null,
                                            level = level,
                                            sequence = ++seq,
                                            profile = profile))
                                }
                            }
                }
    }
}