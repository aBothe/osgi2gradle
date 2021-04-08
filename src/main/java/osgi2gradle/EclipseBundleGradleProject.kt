package osgi2gradle

import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.util.*
import java.util.function.Function
import java.util.jar.Manifest
import java.util.stream.Collectors


class EclipseBundleGradleProject(
        private val buildPropertiesPath: Path,
        val relativePath: Path,
        val gradleSubprojectName: String) : Comparable<EclipseBundleGradleProject?> {
    fun declareProjectSignature(projectsGradleWriter: OutputStreamWriter) {
        projectsGradleWriter
                .append("project(':")
                .append(gradleSubprojectName)
                .append("') {\r\n")
    }

    fun declareProjectEnd(projectsGradleWriter: OutputStreamWriter) {
        projectsGradleWriter.append("}\r\n\r\n")
    }

    fun declareProjectSourceSets(projectsGradleWriter: OutputStreamWriter) {
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
            val jarsToInline = Arrays
                    .stream<String>(bundleProperties.getProperty("bin.includes").split(",").toTypedArray())
                    .map(Function<String, String> { obj: String -> obj.trim { it <= ' ' } })
                    .filter { include: String -> include.toLowerCase().endsWith(".jar") }
                    .map { source: String -> "'$source'" }
                    .collect(Collectors.joining(","))
            if (!jarsToInline.isEmpty()) {
                projectsGradleWriter
                        .append("\tjar.from files(").append(jarsToInline)
                        .append(").collect { zipTree(it) }\r\n")
            }
            val nonJarsToInclude = Arrays
                    .stream<String>(bundleProperties.getProperty("bin.includes").split(",").toTypedArray())
                    .map(Function<String, String> { obj: String -> obj.trim { it <= ' ' } })
                    .filter { include: String -> "." != include && !include.toLowerCase().endsWith(".jar") }
                    .map { source: String -> "'$source'" }
                    .collect(Collectors.joining(","))
            if (nonJarsToInclude.isNotEmpty()) {
                projectsGradleWriter
                        .append("\tjar.from fileTree(projectDir) { includes = [")
                        .append(nonJarsToInclude)
                        .append("] }\r\n")
            }
        }
    }

    private fun readBundleProperties(): Properties {
        val bundleProperties = Properties()
        FileInputStream(buildPropertiesPath.toFile()).use { fin -> bundleProperties.load(fin) }
        return bundleProperties
    }

    fun readManifest(): Manifest? {
        val manifestPath = buildPropertiesPath.parent.resolve("META-INF").resolve("MANIFEST.MF")
        if (!manifestPath.toFile().exists()) {
            return null
        }
        try {
            FileInputStream(manifestPath.toFile()).use { manifestStream -> return Manifest(manifestStream) }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun compareTo(eclipseBundleGradleProject: EclipseBundleGradleProject?): Int {
        return gradleSubprojectName.compareTo(eclipseBundleGradleProject!!.gradleSubprojectName)
    }
}
