package com.github.saiprasadkrishnamurthy.sk8s

import io.reflectoring.diffparser.api.UnifiedDiffParser
import java.io.FileInputStream


object Foo {
    data class Diff(val fileName: String, val from: String, val to: String, val changeType: String)

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = UnifiedDiffParser()
        val i = FileInputStream("sai.txt")
        val diff = parser.parse(i)
        diff.forEach {
            val fileName = (it.fromFileName + it.toFileName).replace("/dev/null", "").substringAfterLast("/")
            println(" File: ${fileName}")
            it.latestHunk.lines
                    .filter { it.lineType.name != "NEUTRAL" }
                    .forEach { l ->
                        println(" [${l.lineType}] ${l.content}")
                    }
            println(" ------------------- ")
        }

    }
}