package osgi2gradle.projectsgradle

import osgi2gradle.EclipseBundleGradleProject
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.stream.Collectors

fun EclipseBundleGradleProject.declareProjectSourceSets(projectsGradleWriter: OutputStreamWriter) {
    val bundleProperties = readBundleProperties()
    if (bundleProperties.containsKey("source..")) {
        projectsGradleWriter
                .append("\tsourceSets.main.java.srcDirs = [")
                .append(Arrays
                        .stream<String>(bundleProperties.getProperty("source..").split(",").toTypedArray())
                        .map { obj: String -> obj.trim { it <= ' ' } }
                        .filter { include: String -> "." != include }
                        .map { source: String -> "'$source'" }
                        .collect(Collectors.joining(",")))
                .append("]\r\n")
    }
    projectsGradleWriter
            .append("\tsourceSets.main.resources.srcDirs = sourceSets.main.java.srcDirs\r\n")
    if (bundleProperties.containsKey("bin.includes")) {
        val jarsToInline = bundleProperties
                .getProperty("bin.includes")
                .split(",")
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { include: String -> include.toLowerCase().endsWith(".jar") }
                .map { source: String -> "'$source'" }
                .joinToString(",")
        if (jarsToInline.isNotEmpty()) {
            projectsGradleWriter
                    .append("\tjar.from files(").append(jarsToInline)
                    .append(").collect { zipTree(it) }\r\n")
        }
        val nonJarsToInclude = bundleProperties
                .getProperty("bin.includes")
                .split(",")
                .map { obj: String -> obj.trim { it <= ' ' } }
                .filter { include: String -> "." != include && !include.toLowerCase().endsWith(".jar") }
                .map { source: String -> "'$source'" }
                .joinToString(",")
        if (nonJarsToInclude.isNotEmpty()) {
            projectsGradleWriter
                    .append("\tjar.from fileTree(projectDir) { includes = [")
                    .append(nonJarsToInclude)
                    .append("] }\r\n")
        }
    }
}

private fun EclipseBundleGradleProject.readBundleProperties(): Properties {
    val bundleProperties = Properties()
    FileInputStream(buildPropertiesPath.toFile()).use { fin -> bundleProperties.load(fin) }
    return bundleProperties
}