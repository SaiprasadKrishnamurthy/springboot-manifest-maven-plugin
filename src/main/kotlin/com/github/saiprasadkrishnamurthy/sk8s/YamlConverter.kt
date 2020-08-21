package com.github.saiprasadkrishnamurthy.sk8s

import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


object YamlConverter {

    fun ymlToProps(file: String): MutableMap<Any, Any> {
        val yaml = Yaml()
        val mutableMap = mutableMapOf<Any, Any>()
        Files.newInputStream(Paths.get(file)).use { s ->
            val config = yaml.loadAs(s, TreeMap::class.java)
            toProperties(config, mutableMap)
        }
        return flatten(mutableMap.toMap())
    }

    private fun toProperties(config: TreeMap<*, *>, mutableMap: MutableMap<Any, Any>) {
        for (key in config.keys) {
            toString(key.toString(), config[key]!!, mutableMap)
        }
    }

    private fun toString(key: String, mapr: Any, mutableMap: MutableMap<Any, Any>) {
        if (mapr !is Map<*, *>) {
            mutableMap[key] = mapr
        } else {
            val map = mapr as Map<String, Any>
            for (mapKey in map.keys) {
                if (map[mapKey] is Map<*, *>) {
                    mutableMap["$key.$mapKey"] = map[mapKey]!!
                } else {
                    mutableMap[String.format("%s.%s", key, mapKey)] = map[mapKey]!!
                }
            }
        }
    }

    fun flatten(map: Map<Any, *>): MutableMap<Any, Any> {
        val processed = mutableMapOf<Any, Any>()
        map.forEach { (key, value) ->
            doFlatten(key, value as Any, processed)
        }
        return processed
    }

    private fun doFlatten(parentKey: Any, value: Any, processed: MutableMap<Any, Any>) {
        if (value is Map<*, *>) {
            value.forEach {
                doFlatten("$parentKey.${it.key}", it.value as Any, processed)
            }
        } else {
            processed[parentKey.toString()] = value
        }
    }
}
