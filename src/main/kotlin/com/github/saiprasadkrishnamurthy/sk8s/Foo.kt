package com.github.saiprasadkrishnamurthy.sk8s

import org.zeroturnaround.exec.ProcessExecutor
import kotlin.random.Random


object Foo {

    @JvmStatic
    fun main(args: Array<String>) {

        val randomValues = List(133) { Random.nextDouble(0.0, 100.0) }
//        println(ASCIIGraph.fromSeries(randomValues.toDoubleArray())
//                .withNumRows(5)
//                .plot())
        println(runCommand("git --no-pager log --oneline --decorate"))
        println()
        println(runCommand("git --no-pager show --pretty= --name-status 62eac0d --first-parent"))
        println()
        println(runCommand("git --no-pager show --pretty= --name-status 62eac0d --first-parent"))
        println()
        println(runCommand("git --no-pager show -s --pretty=\"%an|||||_|||||%at|||||_|||||%cn|||||_|||||%s\" 62eac0d"))


    }

    private fun runCommand(cmd: String): String? =
            ProcessExecutor()
                    .command(cmd.split("\\s".toRegex()))
                    .readOutput(true)
                    .start()
                    .future
                    .get()
                    .outputUTF8()

}