package com.github.saiprasadkrishnamurthy.sk8s

import io.reflectoring.diffparser.api.UnifiedDiffParser
import io.reflectoring.diffparser.api.model.Diff
import java.io.FileInputStream


object Foo {

    @JvmStatic
    fun main(args: Array<String>) {
        val parser = UnifiedDiffParser()
        val i = FileInputStream("sai.txt")
        val diff = parser.parse(i)
        diff.forEach { diff ->
            val fileName = (diff.fromFileName + diff.toFileName).replace("/dev/null", "").substringAfter("/")
            println(" File: ${fileName}")
            val from = contents(diff, "FROM")
            val to = contents(diff, "TO")
            val diff = DiffLog(commitId = "",
                    file = fileName,
                    from = from,
                    to = to,
                    changeType = "",
                    author = "",
                    timestamp = 1,
                    mavenVersion = "",
                    tickets = ""
            )
            println(" ------------------- ")
        }

    }

    private fun contents(diff: Diff, lineType: String) = diff.latestHunk.lines
            .filter { it.lineType.name == lineType }.joinToString("\n") { it.content }
}