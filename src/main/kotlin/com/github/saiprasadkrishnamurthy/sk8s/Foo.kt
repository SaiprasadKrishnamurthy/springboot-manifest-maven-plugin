package com.github.saiprasadkrishnamurthy.sk8s

object Foo {
    @JvmStatic
    fun main(args: Array<String>) {
        YmlPropertiesDocumentationGenerator.newInstance().generateDoc(GeneratePropsDocRequest(outputDir = "",
                artifactId = "artifact-id",
                version = "1.0-SNAPSHOT",
                baseDir = "/Users/saiprasadkrishnamurthy/Downloads/kannan"))
    }
}